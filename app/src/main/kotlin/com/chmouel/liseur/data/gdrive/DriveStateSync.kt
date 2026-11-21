package com.chmouel.liseur.data.gdrive

import android.content.Context
import android.util.Log
import com.chmouel.liseur.data.db.ReadingProgressDao
import com.chmouel.liseur.data.library.AnnotationBackupRepository
import com.chmouel.liseur.data.library.ReadingPositionBackupRepository
import com.chmouel.liseur.data.remote.ServerKind
import com.chmouel.liseur.domain.BackupContents
import com.chmouel.liseur.domain.decodeAnnotationBackup
import com.chmouel.liseur.domain.decodeReadingPositionBackup
import com.chmouel.liseur.domain.encodeAnnotationBackup
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps reading positions, highlights and notes of Drive books in step
 * between devices through the account's private Drive app folder.
 *
 * Each device writes only its own file (`liburuville-android-<id>.json`)
 * and reads everyone else's, so two devices never overwrite each other.
 * A position from another device wins when it was read later than the
 * one here; highlights and notes are merged in, never removed.
 */
class DriveStateSync(
    context: Context,
    private val isDrive: suspend () -> Boolean,
    private val progressDao: ReadingProgressDao,
    private val positions: ReadingPositionBackupRepository,
    private val annotations: AnnotationBackupRepository,
    private val bookDao: com.chmouel.liseur.data.db.BookDao,
    private val sessionDao: com.chmouel.liseur.data.db.ReadingSessionDao,
    private val evaluations: com.chmouel.liseur.data.db.BookEvaluationDao? = null,
    private val refreshCatalog: suspend () -> Unit = {},
    private val http: OkHttpClient = DriveAuth.client(),
) {
    private val app = context.applicationContext
    private val lock = Mutex()
    private val hcChanges = mutableListOf<Pair<String, HardcoverSync.Change>>()
    @Volatile private var lastRun = 0L
    /** When the last full sync ended well, for Ajustes. */
    val lastSyncAt = kotlinx.coroutines.flow.MutableStateFlow(0L)

    private val deviceId: String by lazy {
        val prefs = app.getSharedPreferences("gdrive_sync", Context.MODE_PRIVATE)
        prefs.getString("device", null) ?: UUID.randomUUID().toString().take(8).also {
            prefs.edit().putString("device", it).apply()
        }
    }
    private val myName get() = "$PREFIX$deviceId.json"

    suspend fun syncNow(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastRun < MIN_INTERVAL) return
        if (!isDrive() || DriveAuth.refreshToken() == null) return
        if (!lock.tryLock()) return
        try {
            lastRun = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                runCatching { run() }.onFailure { Log.w(TAG, "sync failed", it) }.onSuccess { lastSyncAt.value = System.currentTimeMillis() }
            }
        } finally {
            lock.unlock()
        }
    }

    private suspend fun run() {
        val files = listFiles()
        // Pull first, so what this device then publishes already includes it.
        for ((id, name) in files) {
            if (name == myName) continue
            val text = download(id) ?: continue
            runCatching { apply(JSONObject(text)) }.onFailure { Log.w(TAG, "could not apply $name", it) }
        }
        val mine = JSONObject()
            .put("format", 1)
            .put("device", deviceId)
            .put("at", System.currentTimeMillis())
            .put("positions", exportPositions())
            .put("annotations", exportAnnotations())
            // Liburuville: what else the reader changes on one device.
            .put("edits", com.chmouel.liseur.ui.library.LiburuEdits.export(app))
            .put("ratings", exportRatings())
            .put("feeds", liburuPrefs.getString("feeds", null) ?: JSONObject.NULL)
            .put("feedsT", liburuPrefs.getLong("feedsT", 0L))
            .put("whereLinks", liburuPrefs.getString("whereLinks", null) ?: JSONObject.NULL)
            .put("whereLinksT", liburuPrefs.getLong("whereLinksT", 0L))
        upload(files.firstOrNull { it.second == myName }?.first, mine.toString())
        runCatching { syncSharedProfile() }.onFailure { Log.w(TAG, "shared profile failed", it) }
        runCatching { exchangeSessions() }.onFailure { Log.w(TAG, "sessions exchange failed", it) }
        // Liburuville: the «Audiolibros» folder, looked at with a session that is known to work.
        runCatching { com.chmouel.liseur.audio.LiburuAudiobooks.scan(app) }.onFailure { Log.w(TAG, "audiobooks scan failed", it) }
        // What each EPUB says of itself (series, cover, synopsis), then the
        // catalog again so the shelf shows it; then covers from the internet.
        runCatching { if (LiburuPeek.fill(bookDao) > 0) refreshCatalog() }.onFailure { Log.w(TAG, "peek failed", it) }
        runCatching { LiburuCovers.fill(bookDao) }.onFailure { Log.w(TAG, "covers failed", it) }
        // New books go to the automatic shelves on their own, as on the web.
        runCatching {
            val placed = com.chmouel.liseur.ui.library.LiburuAutoShelves.autoAssign(app, bookDao.allOnce().filter { !it.hidden })
            if (placed > 0) syncSharedProfile()
        }.onFailure { Log.w(TAG, "auto shelves failed", it) }
    }

    /**
     * The web app's shared file (`state.json`): goals, appearance and profile
     * (name and photo) are merged both ways, newest change winning, and every
     * other part of the file is left exactly as the web wrote it.
     */
    private suspend fun syncSharedProfile() {
        val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name='state.json' and trashed=false")
            .addQueryParameter("fields", "files(id)")
            .build()
        val id = http.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) throw IOException("list state ${it.code}")
            JSONObject(it.body.string()).optJSONArray("files")?.optJSONObject(0)?.optString("id")
        }?.takeIf { it.isNotEmpty() }
        val state = id?.let(::download)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject().put("v", 1).put("books", JSONObject())
        var changed = false
        val prefs = com.chmouel.liseur.ui.library.styles.LiburuPrefs
        val look = com.chmouel.liseur.ui.theme.LiburuAppearance

        // Goals: { daily, yearly, t }
        val goals = state.optJSONObject("goals")
        if (goals != null && goals.optLong("t") > prefs.goalsChangedAt(app)) {
            prefs.setDailyGoal(app, goals.optInt("daily", 30), goals.optLong("t"))
            prefs.setYearGoal(app, goals.optInt("yearly", 24), goals.optLong("t"))
        } else if (prefs.goalsChangedAt(app) > (goals?.optLong("t") ?: 0L)) {
            state.put("goals", JSONObject().put("daily", prefs.dailyGoal(app)).put("yearly", prefs.yearGoal(app)).put("t", prefs.goalsChangedAt(app)))
            changed = true
        }

        // Appearance: { bg, wall, accent, dim, titles, t } — the web's own extras kept.
        look.init(app)
        val appearance = state.optJSONObject("appearance")
        if (appearance != null && appearance.optLong("t") > look.changedAt()) {
            look.adopt(
                appearance.optString("bg").takeIf { it.isNotEmpty() },
                appearance.optString("wall").takeIf { it.isNotEmpty() && it != "null" },
                appearance.optString("accent").takeIf { it.isNotEmpty() },
                appearance.optLong("t"),
            )
        } else if (look.changedAt() > (appearance?.optLong("t") ?: 0L)) {
            val now = look.look.value
            state.put(
                "appearance",
                (appearance ?: JSONObject().put("dim", 0.25).put("titles", false))
                    .put("bg", now.background ?: "crema")
                    .put("wall", now.wallpaper ?: JSONObject.NULL)
                    .put("accent", now.accent)
                    .put("t", look.changedAt()),
            )
            changed = true
        }

        // Profile: { name, avatar (data URL), t }
        val profile = state.optJSONObject("profile")
        if (profile != null && profile.optLong("t") > prefs.profileChangedAt(app)) {
            prefs.setName(app, profile.optString("name").takeIf { it.isNotBlank() }, profile.optLong("t"))
            prefs.adoptAvatarDataUrl(app, profile.optString("avatar").takeIf { it.isNotBlank() })
        } else if (prefs.profileChangedAt(app) > (profile?.optLong("t") ?: 0L)) {
            state.put(
                "profile",
                JSONObject()
                    .put("name", prefs.name(app).orEmpty())
                    .put("avatar", prefs.avatarDataUrl(app).orEmpty())
                    .put("t", prefs.profileChangedAt(app)),
            )
            changed = true
        }

        if (syncBooks(state)) changed = true
        val hadShelfEdits = com.chmouel.liseur.ui.library.LiburuShelves.applyPending(app, state)
        if (hadShelfEdits) changed = true
        if (LiburuShared.mergeVocab(app, state)) changed = true
        if (LiburuShared.mergeWishlist(app, state)) changed = true
        if (LiburuShared.mergeRetos(app, state)) changed = true
        val hadAnn = LiburuShared.applyAnnotations(app, state)
        if (hadAnn) changed = true

        // Kept for the Home level and challenges, which read the web's books, shelves and retos.
        File(app.filesDir, STATE_CACHE).writeText(state.toString())
        if (!changed) return
        state.put("updated", System.currentTimeMillis())
        val json = "application/json".toMediaType()
        val request = if (id != null) {
            Request.Builder().url("$UPLOAD/files/$id?uploadType=media").patch(state.toString().toRequestBody(json)).build()
        } else {
            val meta = JSONObject().put("name", "state.json").put("parents", JSONArray().put("appDataFolder"))
            Request.Builder().url("$UPLOAD/files?uploadType=multipart").post(
                MultipartBody.Builder().setType("multipart/related".toMediaType())
                    .addPart(meta.toString().toRequestBody(json))
                    .addPart(state.toString().toRequestBody(json))
                    .build(),
            ).build()
        }
        http.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("state upload ${it.code}") }
        if (hadAnn) { LiburuShared.clearAnnotations(app); File(app.filesDir, STATE_CACHE).writeText(state.toString()); LiburuShared.version.value++ }
        if (hadShelfEdits) com.chmouel.liseur.ui.library.LiburuShelves.clearPending(app)
        // Only once the shared file is written, so a failed upload is retried with them.
        val hc = HardcoverSync(app, com.chmouel.liseur.data.remote.RemoteHttp.default())
        hcChanges.forEach { (key, change) -> hc.push(key, change); Thread.sleep(1_100) }
        hcChanges.clear()
    }

    private val liburuPrefs get() = app.getSharedPreferences("liburu", Context.MODE_PRIVATE)

    private suspend fun exportRatings(): JSONArray {
        val out = JSONArray()
        evaluations?.all()?.filter { it.bookUrl.startsWith(DRIVE_URL) }?.forEach {
            out.put(JSONObject().put("u", it.bookUrl).put("f", it.field).put("v", it.value ?: JSONObject.NULL).put("t", it.updatedAt))
        }
        return out
    }

    private suspend fun applyLiburu(remote: JSONObject) {
        remote.optJSONObject("edits")?.let { com.chmouel.liseur.ui.library.LiburuEdits.merge(app, it) }
        val dao = evaluations
        remote.optJSONArray("ratings")?.let { arr ->
            if (dao == null) return@let
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val url = r.optString("u"); val field = r.optString("f"); val t = r.optLong("t")
                val value = if (r.isNull("v")) null else r.optString("v")
                val local = dao.get(url, field)
                if (t <= (local?.updatedAt ?: 0L) + 1_000) continue
                val book = bookDao.getByUrl(url) ?: continue
                runCatching { dao.edit(url, field, value, book.title, book.author.orEmpty(), t) }
            }
        }
        val whereT = remote.optLong("whereLinksT")
        if (whereT > liburuPrefs.getLong("whereLinksT", 0L) && !remote.isNull("whereLinks")) {
            liburuPrefs.edit().putString("whereLinks", remote.optString("whereLinks")).putLong("whereLinksT", whereT).apply()
            com.chmouel.liseur.ui.library.WhereLinks.version.value++
        }
        val feedsT = remote.optLong("feedsT")
        if (feedsT > liburuPrefs.getLong("feedsT", 0L) && !remote.isNull("feeds")) {
            liburuPrefs.edit().putString("feeds", remote.optString("feeds")).putLong("feedsT", feedsT).apply()
            java.io.File(app.cacheDir, "liburu-news.json").delete()
        }
    }

    private suspend fun apply(remote: JSONObject) {
        runCatching { applyLiburu(remote) }.onFailure { Log.w(TAG, "liburu extras", it) }
        remote.optString("positions").takeIf { it.isNotEmpty() }?.let { text ->
            val newer = decodeReadingPositionBackup(text).filter { position ->
                if (!position.bookId.startsWith(DRIVE_URL)) return@filter false
                val local = progressDao.get(position.bookId)
                val localAt = local?.readAt ?: local?.updatedAt ?: 0L
                position.readAt > localAt + 1_000
            }
            if (newer.isNotEmpty()) runCatching { positions.restore(newer) }
                .onFailure { Log.i(TAG, "a book is open; its position waits for the next sync") }
        }
        remote.optString("annotations").takeIf { it.isNotEmpty() }?.let { text ->
            val contents = decodeAnnotationBackup(text) as? BackupContents.Readable ?: return@let
            val drive = contents.books.filter { it.bookId.startsWith(DRIVE_URL) }
            if (drive.isNotEmpty()) annotations.importContents(BackupContents.Readable(drive))
        }
    }

    /**
     * Book status, dates and how far through, against the web's `books` map
     * (keyed `g:<Drive id>`; here `gdrive:<Drive id>`). The web's exact place
     * is an EPUB CFI this reader cannot use, so the fraction travels instead.
     */
    private suspend fun syncBooks(state: JSONObject): Boolean {
        val books = state.optJSONObject("books") ?: JSONObject().also { state.put("books", it) }
        val pulls = mutableListOf<com.chmouel.liseur.domain.BackedUpReadingPosition>()
        hcChanges.clear()
        var changed = false
        for (book in bookDao.allOnce()) {
            if (!book.url.startsWith(DRIVE_URL)) continue
            val webId = "g:" + book.url.removePrefix(DRIVE_URL)
            val web = books.optJSONObject(webId)
            val pos = web?.optJSONObject("pos")
            val meta = web?.optJSONObject("meta")
            val webAt = maxOf(pos?.optLong("t") ?: 0L, meta?.optLong("t") ?: 0L)
            val local = progressDao.get(book.url)
            val localAt = local?.updatedAt ?: 0L
            if (web != null && webAt > localAt + 2_000) {
                val webFraction = pos?.optDouble("fraction")?.takeIf { it.isFinite() && it in 0.0..1.0 }
                // Only a place actually newer and actually elsewhere replaces this device's exact one.
                val fraction = webFraction?.takeIf {
                    (pos?.optLong("t") ?: 0L) > localAt + 2_000 &&
                        kotlin.math.abs(it - (local?.totalProgression ?: -1.0)) > 0.005
                }
                val localStatus = when {
                    book.finishedAt != null -> "finished"
                    local?.override == com.chmouel.liseur.domain.FinishedOverride.DID_NOT_FINISH -> "did_not_finish"
                    local?.override == com.chmouel.liseur.domain.FinishedOverride.WANT_TO_READ -> "want_to_read"
                    else -> null
                }
                val status = when (meta?.optString("status")) {
                    "finished" -> "finished"
                    "dropped" -> "did_not_finish"
                    "want" -> "want_to_read"
                    else -> null
                }?.takeIf { it != localStatus && (meta?.optLong("t") ?: 0L) > localAt + 2_000 }
                if (fraction != null || status != null) {
                    pulls += com.chmouel.liseur.domain.BackedUpReadingPosition(
                        bookId = book.url,
                        title = book.title,
                        author = book.author,
                        locatorJson = if (fraction == null) local?.locatorJson ?: "{}" else "{}",
                        progression = fraction ?: local?.totalProgression,
                        readAt = webAt,
                        readingStatus = status,
                    )
                }
            } else if (local != null && localAt > webAt + 2_000) {
                val entry = web ?: JSONObject().put("bookId", webId).put("ann", JSONObject())
                val progression = local.totalProgression?.takeIf { it.isFinite() }
                if (progression != null && progression > 0) {
                    val oldFraction = pos?.optDouble("fraction") ?: -1.0
                    entry.put(
                        "pos",
                        JSONObject()
                            .put("cfi", if (kotlin.math.abs(oldFraction - progression) < 0.001) pos?.optString("cfi").orEmpty() else "")
                            .put("fraction", progression)
                            .put("t", localAt)
                            .put("device", "android-$deviceId"),
                    )
                }
                val status = when {
                    book.finishedAt != null || local.override == com.chmouel.liseur.domain.FinishedOverride.FINISHED -> "finished"
                    local.override == com.chmouel.liseur.domain.FinishedOverride.DID_NOT_FINISH -> "dropped"
                    local.override == com.chmouel.liseur.domain.FinishedOverride.WANT_TO_READ -> "want"
                    (progression ?: 0.0) > 0 -> "reading"
                    else -> meta?.optString("status")?.takeIf { it.isNotEmpty() } ?: "none"
                }
                val newMeta = (meta ?: JSONObject()).put("status", status).put("t", localAt)
                if (status == "finished" && !newMeta.has("finishedAt")) newMeta.put("finishedAt", book.finishedAt ?: localAt)
                if (status == "reading" && !newMeta.has("startedAt")) newMeta.put("startedAt", localAt)
                entry.put("meta", newMeta)
                books.put(webId, entry)
                changed = true
                if (status != (meta?.optString("status") ?: "none")) {
                    hcChanges += webId to HardcoverSync.Change(
                        book.title, book.author, status,
                        newMeta.optLong("startedAt").takeIf { it > 0 }, newMeta.optLong("finishedAt").takeIf { it > 0 },
                    )
                }
            }
        }
        if (pulls.isNotEmpty()) runCatching { positions.restore(pulls) }
            .onFailure { Log.i(TAG, "an open book waits for the next sync") }
        return changed
    }

    /**
     * Reading sessions in the web app's own format, both ways: this device's
     * Drive-book sittings go out as `sessions-android-<id>.json`, which the web
     * reads like any other device's; everyone else's come back for the Home
     * level, streak and challenges.
     */
    private suspend fun exportSessionsJson(): JSONObject {
        val out = JSONArray()
        for (s in sessionDao.allOnceForExport()) {
            if (!s.bookUrl.startsWith(DRIVE_URL) || s.durationMs <= 0) continue
            val end = s.endedAt ?: s.lastCheckpointAt
            out.put(
                JSONObject()
                    .put("id", "and-$deviceId-${s.id}")
                    .put("bookId", "g:" + s.bookUrl.removePrefix(DRIVE_URL))
                    .put("device", "android-$deviceId")
                    .put("deviceName", liburuPrefs.getString("deviceName", null) ?: android.os.Build.MODEL ?: "Android")
                    .put("start", s.startedAt)
                    .put("end", end)
                    .put("seconds", s.durationMs / 1000)
                    .put("fromFrac", s.startProgression ?: JSONObject.NULL)
                    .put("toFrac", s.endProgression ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("v", 1).put("device", "android-$deviceId").put("sessions", out)
    }

    private suspend fun exchangeSessions() {
        val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name contains 'sessions-' and trashed=false")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "100")
            .build()
        val files = http.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) throw IOException("list sessions ${it.code}")
            val arr = JSONObject(it.body.string()).optJSONArray("files") ?: JSONArray()
            (0 until arr.length()).map { i -> arr.getJSONObject(i).let { f -> f.getString("id") to f.getString("name") } }
        }
        val mineName = "sessions-android-$deviceId.json"
        val others = JSONArray()
        for ((id, name) in files) {
            if (name == mineName) continue
            val text = download(id) ?: continue
            val list = runCatching { JSONObject(text).optJSONArray("sessions") }.getOrNull() ?: continue
            for (i in 0 until list.length()) others.put(list.get(i))
        }
        File(app.filesDir, SESSIONS_CACHE).writeText(others.toString())
        val body = exportSessionsJson().toString()
        val existing = files.firstOrNull { it.second == mineName }?.first
        val json = "application/json".toMediaType()
        val request = if (existing != null) {
            Request.Builder().url("$UPLOAD/files/$existing?uploadType=media").patch(body.toRequestBody(json)).build()
        } else {
            val meta = JSONObject().put("name", mineName).put("parents", JSONArray().put("appDataFolder"))
            Request.Builder().url("$UPLOAD/files?uploadType=multipart").post(
                MultipartBody.Builder().setType("multipart/related".toMediaType())
                    .addPart(meta.toString().toRequestBody(json))
                    .addPart(body.toRequestBody(json))
                    .build(),
            ).build()
        }
        http.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("sessions upload ${it.code}") }
    }

    private suspend fun exportPositions(): String {
        val file = File.createTempFile("positions", ".json", app.cacheDir)
        try {
            positions.writeContents(file, MAX_BYTES)
            val root = JSONObject(file.readText())
            val all = root.getJSONArray("positions")
            val kept = JSONArray()
            for (i in 0 until all.length()) {
                val entry = all.getJSONObject(i)
                if (entry.optString("book_id").startsWith(DRIVE_URL)) kept.put(entry)
            }
            return root.put("positions", kept).toString()
        } finally {
            file.delete()
        }
    }

    private suspend fun exportAnnotations(): String {
        val books = annotations.exportContents().books.filter { it.bookId.startsWith(DRIVE_URL) }
        return encodeAnnotationBackup(books)
    }

    private fun listFiles(): List<Pair<String, String>> {
        val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name contains '$PREFIX' and trashed=false")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "100")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("list ${response.code}")
            val files = JSONObject(response.body.string()).optJSONArray("files") ?: return emptyList()
            return (0 until files.length()).map { files.getJSONObject(it).let { f -> f.getString("id") to f.getString("name") } }
        }
    }

    private fun download(id: String): String? =
        http.newCall(Request.Builder().url("${DriveAuth.BASE_URL}/files/$id?alt=media").build()).execute().use {
            if (it.isSuccessful) it.body.string() else null
        }

    private fun upload(existingId: String?, body: String) {
        val json = "application/json".toMediaType()
        val request = if (existingId != null) {
            Request.Builder()
                .url("$UPLOAD/files/$existingId?uploadType=media")
                .patch(body.toRequestBody(json))
                .build()
        } else {
            val meta = JSONObject().put("name", myName).put("parents", JSONArray().put("appDataFolder"))
            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(meta.toString().toRequestBody(json))
                .addPart(body.toRequestBody(json))
                .build()
            Request.Builder().url("$UPLOAD/files?uploadType=multipart").post(multipart).build()
        }
        http.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("upload ${it.code}") }
    }

    companion object {
        private const val TAG = "DriveStateSync"
        private const val PREFIX = "liburuville-android-"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private val DRIVE_URL = "${ServerKind.DRIVE.urlPrefix}:"
        private const val MAX_BYTES = 20L * 1024 * 1024
        private const val MIN_INTERVAL = 45_000L
        const val STATE_CACHE = "liburu-state.json"
        const val SESSIONS_CACHE = "liburu-web-sessions.json"
    }
}
