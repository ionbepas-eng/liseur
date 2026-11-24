package com.chmouel.liseur.translate

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import com.chmouel.liseur.R
import com.chmouel.liseur.data.settings.AppSettings
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Liburuville: MyMemory, the free translator the web app uses. Needs no key and no setup, so
 * «Traducir» works on a fresh install; the passage goes to api.mymemory.translated.net.
 */
internal class MyMemoryTranslationService : TranslationService {
    override val id = ID
    override val label = R.string.liburu_translation_mymemory
    override val summary = R.string.liburu_translation_mymemory_summary
    override val icon = Icons.Outlined.Translate
    override val configured: Flow<Boolean> = flowOf(true)
    override val detectsLanguage = false

    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    override fun destination(s: AppSettings): String = "api.mymemory.translated.net"
    override fun owner(s: AppSettings): String = "MyMemory"

    override suspend fun targets(source: String?): Map<String, PairState> =
        LANGS.filter { source == null || !TranslationLanguages.same(it, source) }.associateWith { PairState.Ready }

    override suspend fun sources(): Set<String> = LANGS.toSet()

    override suspend fun translate(passage: String, source: String?, target: String, context: String?, book: String?): String =
        withContext(Dispatchers.IO) {
            val from = source?.substringBefore('-') ?: "en"
            val to = target.substringBefore('-')
            chunks(passage).joinToString(" ") { piece -> one(piece, from, to) }.trim()
                .ifEmpty { throw TranslationError.Empty() }
        }

    private fun one(text: String, from: String, to: String): String {
        val url = "https://api.mymemory.translated.net/get".toHttpUrl().newBuilder()
            .addQueryParameter("q", text).addQueryParameter("langpair", "$from|$to").build()
        val body = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.code == 429) throw TranslationError.RateLimited(429)
                if (!r.isSuccessful) throw TranslationError.Service(r.code)
                r.body.string()
            }
        } catch (e: IOException) {
            throw TranslationError.Network(e)
        }
        val j = JSONObject(body)
        val out = j.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        if (j.optInt("responseStatus", 200) != 200 || out.isBlank() || out.startsWith("MYMEMORY WARNING")) {
            throw TranslationError.Failed(j.optString("responseDetails").ifBlank { "MyMemory" })
        }
        return android.text.Html.fromHtml(out, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    }

    /** MyMemory takes 500 bytes a request: whole sentences, packed up to that. */
    private fun chunks(text: String): List<String> {
        val sentences = text.split(Regex("(?<=[.!?…;])\\s+"))
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (s in sentences) {
            if (cur.isNotEmpty() && (cur.length + s.length + 1).toBytes() > 450) { out += cur.toString(); cur.clear() }
            if (s.toByteArray().size > 450) { s.chunked(140).forEach { out += it } } else { if (cur.isNotEmpty()) cur.append(' '); cur.append(s) }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun Int.toBytes() = this * 2

    @Composable
    override fun SettingsRows(onManageServices: () -> Unit, onOpenPage: () -> Unit) = Unit

    companion object {
        const val ID = "mymemory"
        private val LANGS = listOf("es", "en", "fr", "de", "it", "pt", "ca", "eu", "gl", "nl", "ja", "zh", "ru", "pl")
    }
}
