package com.chmouel.liseur.reader.dictionary

import android.content.Context
import org.json.JSONObject

/**
 * The web's offline English→Spanish dictionary (Wiktionary, CC BY-SA, via
 * doozan/spanish_data; about 81,000 words), bundled with the app so a word
 * in an English book gets its Spanish without a connection.
 */
object LiburuEnEs {
    @Volatile private var map: JSONObject? = null

    private fun load(context: Context): JSONObject = map ?: synchronized(this) {
        map ?: JSONObject(context.assets.open("dict/en-es.json").bufferedReader().use { it.readText() }).also { map = it }
    }

    private val IRREG = mapOf(
        "was" to "be", "were" to "be", "been" to "be", "am" to "be", "is" to "be", "are" to "be", "has" to "have", "had" to "have",
        "did" to "do", "does" to "do", "done" to "do", "went" to "go", "gone" to "go", "said" to "say", "made" to "make", "took" to "take",
        "taken" to "take", "came" to "come", "saw" to "see", "seen" to "see", "knew" to "know", "known" to "know", "got" to "get",
        "gave" to "give", "given" to "give", "found" to "find", "thought" to "think", "told" to "tell", "became" to "become", "left" to "leave",
        "felt" to "feel", "brought" to "bring", "began" to "begin", "kept" to "keep", "held" to "hold", "wrote" to "write", "written" to "write",
        "stood" to "stand", "heard" to "hear", "meant" to "mean", "met" to "meet", "ran" to "run", "paid" to "pay", "sat" to "sit",
        "spoke" to "speak", "led" to "lead", "grew" to "grow", "lost" to "lose", "fell" to "fall", "sent" to "send", "built" to "build",
        "understood" to "understand", "drew" to "draw", "broke" to "break", "spent" to "spend", "drove" to "drive", "bought" to "buy",
        "wore" to "wear", "chose" to "choose", "threw" to "throw", "caught" to "catch", "fought" to "fight", "taught" to "teach", "ate" to "eat",
        "slept" to "sleep", "flew" to "fly", "forgot" to "forget", "hid" to "hide", "shook" to "shake", "stole" to "steal", "woke" to "wake",
        "men" to "man", "women" to "woman", "children" to "child", "feet" to "foot", "teeth" to "tooth", "mice" to "mouse",
        "better" to "good", "best" to "good", "worse" to "bad", "worst" to "bad", "lives" to "life", "wives" to "wife", "knives" to "knife",
        "leaves" to "leaf", "selves" to "self", "wolves" to "wolf", "halves" to "half", "shelves" to "shelf",
    )

    /** The word and the forms it may come from, as the web's lemmas(). */
    fun lemmas(word: String): List<String> {
        val w = word.lowercase().replace(Regex("['’]s$"), "").replace(Regex("[^a-z\\-']"), "")
        val out = linkedSetOf(w)
        IRREG[w]?.let { out += it }
        fun add(x: String) { if (x.length > 1) out += x }
        if (w.endsWith("ies")) add(w.dropLast(3) + "y")
        if (w.endsWith("es")) add(w.dropLast(2))
        if (w.endsWith("s") && !w.endsWith("ss")) add(w.dropLast(1))
        if (w.endsWith("ied")) add(w.dropLast(3) + "y")
        if (w.endsWith("ed")) { add(w.dropLast(2)); add(w.dropLast(1)); if (Regex("(.)\\1ed$").containsMatchIn(w)) add(w.dropLast(3)) }
        if (w.endsWith("ing")) { add(w.dropLast(3)); add(w.dropLast(3) + "e"); if (Regex("(.)\\1ing$").containsMatchIn(w)) add(w.dropLast(4)) }
        if (w.endsWith("ly")) { add(w.dropLast(2)); if (w.endsWith("ily")) add(w.dropLast(3) + "y") }
        if (w.endsWith("er")) { add(w.dropLast(2)); add(w.dropLast(1)); if (w.endsWith("ier")) add(w.dropLast(3) + "y") }
        if (w.endsWith("est")) { add(w.dropLast(3)); add(w.dropLast(2)) }
        if (w.endsWith("ness")) add(w.dropLast(4))
        return out.filter { it.isNotEmpty() }
    }

    private val POS = mapOf("n" to "sust.", "v" to "verbo", "adj" to "adj.", "adv" to "adv.", "prep" to "prep.", "conj" to "conj.",
        "pron" to "pron.", "interj" to "interj.", "art" to "art.", "determiner" to "det.")

    data class Hit(val lemma: String, val es: List<Pair<String, String>>)

    /** Only when the dictionary is already in memory: safe to call on the main thread. */
    fun lookupLoaded(context: Context, phrase: String): Hit? = if (map == null) null else lookup(context, phrase)

    fun lookup(context: Context, phrase: String): Hit? {
        val d = runCatching { load(context) }.getOrNull() ?: return null
        val p = phrase.lowercase().trim()
        for (k in listOf(p) + lemmas(p)) {
            val a = d.optJSONArray(k) ?: continue
            // The source carries some SMS spellings («100pre»); they are left out.
            val es = (0 until a.length()).map { a.getString(it).split('|') }
                .map { it[0] to (POS[it.getOrNull(1)] ?: it.getOrNull(1).orEmpty()) }
                .filter { !it.first.any(Char::isDigit) }
            if (es.isNotEmpty()) return Hit(k, es.distinctBy { it.first }.take(8))
        }
        return null
    }
}
