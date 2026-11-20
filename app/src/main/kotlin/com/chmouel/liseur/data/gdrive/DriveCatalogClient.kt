package com.chmouel.liseur.data.gdrive

import android.util.Log
import com.chmouel.liseur.data.remote.CatalogSource
import com.chmouel.liseur.data.remote.CatalogWalk
import com.chmouel.liseur.data.remote.RemoteBook
import com.chmouel.liseur.data.remote.RemoteCredentials
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The «Libros» folder in Google Drive, walked with its subfolders, as a
 * catalog of EPUB books. The audiobook folder is left out.
 */
class DriveCatalogClient(private val http: OkHttpClient = DriveAuth.client()) : CatalogSource {

    override suspend fun allBooks(
        baseUrl: String,
        credentials: RemoteCredentials,
        onPage: suspend (List<RemoteBook>) -> Unit,
    ): CatalogWalk = withContext(Dispatchers.IO) {
        val root = libraryFolder() ?: return@withContext CatalogWalk(complete = false)
        val queue = ArrayDeque(listOf(root))
        val seen = mutableSetOf<String>()
        var complete = true
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val folder = queue.removeFirst()
            if (!seen.add(folder)) continue
            var pageToken: String? = null
            do {
                val page = try {
                    list("'$folder' in parents and trashed=false", pageToken)
                } catch (e: IOException) {
                    Log.w(TAG, "listing $folder failed", e)
                    complete = false
                    null
                } ?: break
                val books = mutableListOf<RemoteBook>()
                val files = page.optJSONArray("files")
                for (i in 0 until (files?.length() ?: 0)) {
                    val f = files!!.getJSONObject(i)
                    val name = f.optString("name")
                    if (f.optString("mimeType") == FOLDER) {
                        if (!name.equals("Audiolibros", ignoreCase = true)) queue += f.getString("id")
                        continue
                    }
                    if (!name.lowercase().let { it.endsWith(".epub") || it.endsWith(".cbz") }) continue
                    if (name.lowercase().endsWith(".cbz")) {
                        com.chmouel.liseur.ui.library.LiburuComics.mark("${com.chmouel.liseur.data.remote.ServerKind.DRIVE.urlPrefix}:" + f.getString("id"))
                    }
                    books += book(f)
                }
                if (books.isNotEmpty()) onPage(books)
                pageToken = page.optString("nextPageToken").takeIf { it.isNotEmpty() }
            } while (pageToken != null)
        }
        CatalogWalk(complete = complete)
    }

    override suspend fun search(
        baseUrl: String,
        credentials: RemoteCredentials,
        query: String,
    ): List<RemoteBook> = withContext(Dispatchers.IO) {
        val q = query.replace("\\", "\\\\").replace("'", "\\'")
        val page = list("name contains '$q' and trashed=false and mimeType='application/epub+zip'", null)
        val files = page.optJSONArray("files") ?: return@withContext emptyList()
        (0 until files.length()).map { book(files.getJSONObject(it)) }
    }

    private fun libraryFolder(): String? {
        val inRoot = list("name='Libros' and mimeType='$FOLDER' and 'root' in parents and trashed=false", null)
        inRoot.optJSONArray("files")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }?.let { return it }
        val anywhere = list("name='Libros' and mimeType='$FOLDER' and trashed=false", null)
        return anywhere.optJSONArray("files")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
    }

    private fun list(q: String, pageToken: String?): JSONObject {
        val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink)")
            .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("Drive ${response.code}")
            return JSONObject(text)
        }
    }

    private fun book(f: JSONObject): RemoteBook {
        val id = f.getString("id")
        val (title, author) = DriveNames.titleAndAuthor(f.optString("name"))
        return RemoteBook(
            remoteId = id,
            title = title,
            author = author,
            coverHref = f.optString("thumbnailLink").takeIf { it.isNotEmpty() }
                ?.replace(Regex("=s\\d+$"), "=s600") ?: LiburuCovers.cached(id),
            downloadHref = "${DriveAuth.BASE_URL}/files/$id?alt=media",
            sizeBytes = f.optString("size").toLongOrNull(),
            updatedAt = f.optString("modifiedTime").takeIf { it.isNotEmpty() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        )
    }

    private companion object {
        const val TAG = "DriveCatalog"
        const val FOLDER = "application/vnd.google-apps.folder"
    }
}

/** Title and author guessed from a file name, until the EPUB itself is read. */
object DriveNames {
    fun titleAndAuthor(fileName: String): Pair<String, String?> {
        var s = fileName.substringBeforeLast('.')
            .replace(Regex("^_?OceanofPDF\\.com_", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^ebin\\.pub_", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\((z-library|z-lib|1lib)[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Spanish Edition\\)", RegexOption.IGNORE_CASE), "")
        if (!s.contains(' ') && s.contains('_')) s = s.replace("_-_", " - ").replace('_', ' ')
        s = s.replace(Regex("^\\d{1,3}\\s*-\\s*"), "").trim()
        Regex("^(.*?)\\s*\\(([^()]+)\\)$").find(s)?.let { m ->
            return m.groupValues[1].trim() to m.groupValues[2].trim()
        }
        val parts = s.split(" - ")
        if (parts.size >= 2) return parts.dropLast(1).joinToString(" - ").trim() to parts.last().trim()
        return s to null
    }
}
