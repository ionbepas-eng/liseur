package com.chmouel.liseur.data.gdrive

import android.content.Context
import android.util.Log
import java.io.IOException
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Marks reading on Hardcover through the user's own Cloudflare Worker,
 * as Liburuville web does (hcsync.js): status, then the read's dates.
 * The Worker keeps the Hardcover token; this app never sees it.
 */
class HardcoverSync(private val context: Context, private val http: OkHttpClient) {
    data class Change(val title: String, val author: String?, val status: String, val startedAt: Long?, val finishedAt: Long?)

    private val prefs = context.getSharedPreferences("liburu", Context.MODE_PRIVATE)
    private val proxy get() = prefs.getString("hcProxy", DEFAULT_PROXY)!!.trimEnd('/')
    val enabled get() = prefs.getBoolean("hcSync", true)

    private fun gql(query: String, variables: JSONObject): JSONObject? {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
        val request = Request.Builder().url(proxy)
            .header("Origin", "https://liburuville.netlify.app")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use {
            val j = JSONObject(it.body.string())
            j.optJSONArray("errors")?.optJSONObject(0)?.let { e -> throw IOException("Hardcover: " + e.optString("message")) }
            if (!it.isSuccessful) throw IOException("Hardcover ${it.code}: ${j.optString("error")}")
            return j.optJSONObject("data")
        }
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun short(t: String) = t.split(Regex("[:(\\[]"))[0].trim()
    private fun sameTitle(a: String, b: String) = norm(short(a)).let { x -> norm(short(b)).let { y -> x == y || x.startsWith(y) || y.startsWith(x) } }

    private fun idFor(key: String, c: Change): Int? {
        val map = JSONObject(prefs.getString("hcMap", "{}")!!)
        if (map.has(key)) return map.getInt(key)
        val q = "${short(c.title)} ${c.author.orEmpty()}".trim()
        val d = gql("""query Buscar(${'$'}q: String!) { search(query: ${'$'}q, query_type: "Book", per_page: 6, page: 1) { results } }""", JSONObject().put("q", q))
        val hits = d?.optJSONObject("search")?.optJSONObject("results")?.optJSONArray("hits") ?: return null
        for (i in 0 until hits.length()) {
            val doc = hits.optJSONObject(i)?.optJSONObject("document") ?: continue
            if (sameTitle(doc.optString("title"), c.title)) {
                val id = doc.optString("id").toIntOrNull() ?: continue
                prefs.edit().putString("hcMap", map.put(key, id).toString()).apply()
                return id
            }
        }
        return null
    }

    private fun day(ts: Long?) = ts?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }

    fun push(key: String, c: Change) {
        if (!enabled) return
        val statusId = STATUS[c.status] ?: return
        runCatching {
            val id = idFor(key, c) ?: throw IOException("No encuentro «${c.title}» en Hardcover")
            val me = gql(
                """query Mio(${'$'}id: Int!) { me { user_books(where: {book_id: {_eq: ${'$'}id}}) { id status_id user_book_reads(order_by: {id: desc}, limit: 1) { id started_at finished_at } } } }""",
                JSONObject().put("id", id),
            )
            val meNode = me?.opt("me").let { if (it is org.json.JSONArray) it.optJSONObject(0) else it as? JSONObject }
            val ub = meNode?.optJSONArray("user_books")?.optJSONObject(0)
            var ubId = ub?.optInt("id")?.takeIf { it > 0 }
            if (ubId == null) {
                val r = gql(
                    """mutation Alta(${'$'}o: UserBookCreateInput!) { insert_user_book(object: ${'$'}o) { id error } }""",
                    JSONObject().put("o", JSONObject().put("book_id", id).put("status_id", statusId)),
                )
                ubId = r?.optJSONObject("insert_user_book")?.optInt("id")
            } else if (ub?.optInt("status_id") != statusId) {
                gql(
                    """mutation Cambio(${'$'}id: Int!, ${'$'}o: UserBookUpdateInput!) { update_user_book(id: ${'$'}id, object: ${'$'}o) { id error } }""",
                    JSONObject().put("id", ubId).put("o", JSONObject().put("status_id", statusId)),
                )
            }
            val started = day(c.startedAt)
            val finished = if (c.status == "finished") day(c.finishedAt) else null
            if (ubId != null && (started != null || finished != null)) {
                val rd = ub?.optJSONArray("user_book_reads")?.optJSONObject(0)
                val dates = JSONObject().put("started_at", started ?: JSONObject.NULL).put("finished_at", finished ?: JSONObject.NULL)
                if (rd == null) {
                    gql(
                        """mutation Lectura(${'$'}id: Int!, ${'$'}d: DatesReadInput!) { insert_user_book_read(user_book_id: ${'$'}id, user_book_read: ${'$'}d) { id error } }""",
                        JSONObject().put("id", ubId).put("d", dates),
                    )
                } else if (rd.optString("started_at") != (started ?: "") || (finished != null && rd.optString("finished_at") != finished)) {
                    gql(
                        """mutation Fechas(${'$'}id: Int!, ${'$'}d: DatesReadInput!) { update_user_book_read(id: ${'$'}id, object: ${'$'}d) { id error } }""",
                        JSONObject().put("id", rd.optInt("id")).put(
                            "d",
                            JSONObject().put("started_at", started ?: rd.opt("started_at")).put("finished_at", finished ?: rd.opt("finished_at")),
                        ),
                    )
                }
            }
            prefs.edit().putString("hcLast", "«${c.title}» marcado en Hardcover").apply()
        }.onFailure {
            Log.w("Hardcover", "push failed", it)
            prefs.edit().putString("hcLast", it.message ?: "Error con Hardcover").apply()
        }
    }

    companion object {
        const val DEFAULT_PROXY = "https://liburu-hardcover.ionbe22.workers.dev"
        private val STATUS = mapOf("want" to 1, "reading" to 2, "finished" to 3, "dropped" to 5)
    }
}
