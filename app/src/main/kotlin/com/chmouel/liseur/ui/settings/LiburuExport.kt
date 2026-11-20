package com.chmouel.liseur.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.chmouel.liseur.container
import com.chmouel.liseur.data.gdrive.DriveStateSync
import com.chmouel.liseur.data.gdrive.LiburuPeek
import com.chmouel.liseur.ui.library.LiburuEdits
import com.chmouel.liseur.ui.library.LiburuEnrich
import com.chmouel.liseur.ui.library.LiburuShelves
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Your data out of the app, as the web offers it: a CSV of sessions and books, and Babelio's import file. */
object LiburuExport {
    private fun q(v: Any?): String { val s = v?.toString().orEmpty(); return if (Regex("[\",\\n]").containsMatchIn(s)) "\"" + s.replace("\"", "\"\"") + "\"" else s }
    private fun day(ms: Long?) = ms?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    private fun share(context: Context, files: List<File>, mime: String, title: String) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, context.packageName + ".quotes", it) })
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        send.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun dir(context: Context) = File(context.cacheDir, "quotes").apply { mkdirs() }

    suspend fun csv(context: Context) {
        val files = withContext(Dispatchers.IO) {
            val db = context.container.database
            val books = db.bookDao().allOnce()
            val byUrl = books.associateBy { it.url }
            val ses = db.readingSessionDao().allOnceForExport()
            val s = StringBuilder("﻿libro,autor,inicio,fin,minutos,desde,hasta\r\n")
            ses.forEach { x ->
                val b = byUrl[x.bookUrl]
                s.append(listOf(b?.title, b?.author, Instant.ofEpochMilli(x.startedAt), x.endedAt?.let { Instant.ofEpochMilli(it) }, x.durationMs / 60000,
                    x.startProgression?.let { "%.3f".format(it) }, x.endProgression?.let { "%.3f".format(it) }).joinToString(",") { q(it) }).append("\r\n")
            }
            val b = StringBuilder("﻿título,autor,serie,número,terminado\r\n")
            books.filter { !it.hidden }.forEach { x -> b.append(listOf(x.title, x.author, x.seriesName, x.seriesIndex, day(x.finishedAt)).joinToString(",") { q(it) }).append("\r\n") }
            listOf(File(dir(context), "sesiones.csv").apply { writeText(s.toString()) }, File(dir(context), "libros.csv").apply { writeText(b.toString()) })
        }
        share(context, files, "text/csv", "Exportar mis datos")
    }

    /** Builds ModeleImportBabelio.csv; returns a line for Ajustes. */
    suspend fun babelio(context: Context): String {
        val (files, line) = withContext(Dispatchers.IO) {
            val books = context.container.database.bookDao().allOnce().filter { !it.hidden }
            val state = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull() ?: JSONObject()
            val web = state.optJSONObject("books") ?: JSONObject()
            val (shelves, membership) = LiburuShelves.load(context)
            val names = shelves.associate { it.id to it.name }
            val rows = ArrayList<String>()
            val missing = ArrayList<String>()
            for (b in books) {
                val key = LiburuShelves.keyOf(b.url)
                val isbn = listOfNotNull(
                    LiburuEdits.get(context, b.url)["isbn"],
                    b.url.takeIf { it.startsWith("gdrive:") }?.let { LiburuPeek.of(it.removePrefix("gdrive:"))?.optString("isbn") },
                    LiburuEnrich.get(context, b.url)?.optJSONObject("gb")?.optString("isbn13"),
                ).map { it.filter(Char::isDigit) }.firstOrNull { it.length == 13 && (it.startsWith("978") || it.startsWith("979")) }
                if (isbn == null) { missing += listOf(b.title, b.author).joinToString(",") { q(it) }; continue }
                val m = web.optJSONObject(key)?.optJSONObject("meta")
                val st = m?.optString("status") ?: if (b.finished) "finished" else "none"
                val status = when (st) { "finished" -> "Lu"; "reading" -> "En cours"; else -> "A lire" }
                val sh = membership[key].orEmpty().mapNotNull { names[it] }.toMutableList()
                if (st == "dropped") sh += "abandonnés"
                val rating = m?.optDouble("rating")?.takeIf { !it.isNaN() && it > 0 }?.toString()?.replace('.', ',').orEmpty()
                val fin = if (st == "finished") day(m?.optLong("finishedAt")?.takeIf { it > 0 } ?: b.finishedAt)?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) }.orEmpty() else ""
                rows += listOf(isbn, status, "Oui", rating, sh.joinToString(","), m?.optString("review").orEmpty(), fin).joinToString(",") { q(it) }
            }
            val head = "EAN, Statut (Lu/A lire/Wishlist/En cours),Possédé,Ma note,Etagères séparées par virgules,Critique,Date de fin de lecture"
            val f1 = File(dir(context), "ImportBabelio.csv").apply { writeText("﻿" + (listOf(head) + rows).joinToString("\r\n") + "\r\n") }
            val f2 = File(dir(context), "Babelio-a-mano.csv").apply { writeText("﻿" + (listOf("Título,Autor") + missing).joinToString("\r\n") + "\r\n") }
            listOf(f1, f2) to "${rows.size} libros con ISBN · ${missing.size} para añadir a mano"
        }
        share(context, files, "text/csv", "Exportar para Babelio")
        return line
    }
}
