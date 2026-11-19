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
    private val http: OkHttpClient = DriveAuth.client(),
) {
    private val app = context.applicationContext
    private val lock = Mutex()
    @Volatile private var lastRun = 0L

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
            withContext(Dispatchers.IO) { runCatching { run() }.onFailure { Log.w(TAG, "sync failed", it) } }
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
        upload(files.firstOrNull { it.second == myName }?.first, mine.toString())
    }

    private suspend fun apply(remote: JSONObject) {
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
    }
}
