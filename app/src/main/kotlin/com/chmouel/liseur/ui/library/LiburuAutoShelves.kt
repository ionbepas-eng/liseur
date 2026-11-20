package com.chmouel.liseur.ui.library

import android.content.Context
import com.chmouel.liseur.container
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.gdrive.LiburuCovers

/**
 * The web's automatic shelves (autoshelves.js): each book goes to the shelves
 * its genres, title, author and year point at. A book can go to several;
 * none is ever taken off a shelf.
 */
object LiburuAutoShelves {
    private class Rule(
        val name: String,
        val re: Regex,
        val notIf: Regex? = null,
        val before: Int? = null,
        val authors: Regex? = null,
    )

    private fun rx(s: String) = Regex(s, setOf(RegexOption.IGNORE_CASE))
    private fun authors(vararg list: String) = Regex("\\b(" + list.joinToString("|") + ")", RegexOption.IGNORE_CASE)

    private val RULES = listOf(
        Rule("Cine", rx("""\b(cine|cinema|cinéma|films?|movies?|hollywood|pel[ií]culas?|cineastas?|directores? de cine|screen(play|writ)|oscars?|westerns?|filmmak|cin[eé]filo|performing arts / film|tarantino|almod[oó]var|hitchcock|kubrick|spielberg|wilder|wes anderson)"""), authors = authors("""Roger Ebert""", """Pedro Almod""", """Quentin Tarantino""", """Peter Biskind""", """Ian Nathan""")),
        Rule("Fitness", rx("""\b(fitness|calisthenics?|calistenia|bodybuilding|culturismo|workouts?|entrenamiento|strength training|isometric|ejercicios?|exercise|muscul|gym|running|yoga|nutrici[oó]n|nutrition|diet(a|s)?\b|low carb|keto|high protein|cookbook|recetas|cooking|cocina|health & fitness)""")),
        Rule("Historia", rx("""\b(historia|history|historical|hist[oó]ric|world war|guerra mundial|wwii|ww2|guerra civil|civil war|segunda guerra|nazis?|holocaust|imperio|empire|antigua roma|ancient|medieval history|edad media)"""), notIf = rx("""historia del cine|history of (film|cinema|movies)|history & criticism|historia y cr[ií]tica""")),
        Rule("Biografías y memorias", rx("""\b(biograf[ií]as?|biograph(y|ies)|memoirs?|memorias|autobiograf|biography & autobiography|diarios|letters|cartas|vida de)""")),
        Rule("LGTB", rx("""\b(lgbt\w*|lgtb\w*|gays?|lesbian\w*|lesbianas?|homosexual\w*|queer|transg[eé]ner\w*|transgender|bisexual\w*|homoerot\w*|maric[ao]s?)\b"""), authors = authors("""James Baldwin""", """Alan Hollinghurst""", """Edmund White""", """Andr[eé] Aciman""", """Terenci Moix""", """Luis Cernuda""", """Christopher Isherwood""", """Armistead Maupin""", """Garth Greenwell""", """Ocean Vuong""", """Vito Russo""", """Pedro Lemebel""", """Reinaldo Arenas""", """Manuel Puig""")),
        Rule("Fantasía Medieval", rx("""\b(epic fantasy|high fantasy|fantas[ií]a [eé]pica|alta fantas[ií]a|medieval fantasy|sword (and|&) sorcery|espada y brujer[ií]a|tierra media|middle-earth|tolkien|silmarillion|se[nñ]or de los anillos|lord of the rings|hobbit|poniente|westeros|rueda del tiempo|wheel of time|caballeros|knights|reinos|kingdoms|dragon(es|s)?)"""), authors = authors("""J\.?\s?R\.?\s?R\.? Tolkien""", """George R\.?\s?R\.? Martin""", """Robert Jordan""", """Brandon Sanderson""", """Patrick Rothfuss""", """Joe Abercrombie""", """Robin Hobb""", """Robert Foster""", """Christopher Tolkien""", """David Day""", """J\.?\s?E\.?\s?A\.? Tyler""", """Guy Gavriel Kay""", """Steven Erikson""")),
        Rule("Fantasía", rx("""\b(fantas[yí]a|fantasy|magia|magic|wizards?|brujas?|witch(es)?|elfos|elves|cuentos de hadas|fairy tales?)"""), notIf = rx("""fantas[ií]as? (sexual|er[oó]tic)"""), authors = authors("""Gregory Maguire""", """Ursula K""", """Terry Pratchett""", """Neil Gaiman""", """Michael Ende""", """Laura Gallego""")),
        Rule("Ciencia ficción", rx("""\b(science fiction|ciencia ficci[oó]n|sci-?fi|space opera|dist[oó]pi\w*|dystopi\w*|cyberpunk|robots?|alien)"""), authors = authors("""Frank Herbert""", """Isaac Asimov""", """Philip K""", """Arthur C""", """Ray Bradbury""", """William Gibson""", """Ursula K""", """Liu Cixin""", """Dan Simmons""")),
        Rule("Cómic y novela gráfica", rx("""\b(c[oó]mics?|graphic novels?|novela gr[aá]fica|manga|tebeos?|comics & graphic novels)""")),
        Rule("Pop", rx("""\b(cultura pop|pop culture|popular culture|m[uú]sica|music(al)?|rock|jazz|hip hop|pop stars?|celebrit\w*|celebridad\w*|cantantes?|singers?|discos?|televisi[oó]n|television|tv series|series de tv|moda|fashion|iconos?|icons?|beyonc[eé]|beatles|bowie|madonna|lady gaga|britney|los (ochenta|noventa)|the (eighties|nineties)|90s|80s)"""), authors = authors("""Chuck Klosterman""", """Rob Sheffield""", """Simon Reynolds""", """Elena Herrera""")),
        Rule("Ensayo", rx("""\b(ensayos?|essays?|nonfiction|non-fiction|no ficci[oó]n|cr[ií]tica|criticism|filosof[ií]a|philosophy|sociolog\w*|social science|pol[ií]tica|political|cultura pop|pop culture|antropolog\w*|psicolog\w*|psychology|feminis\w*|periodismo|journalism|self-help|autoayuda|desarrollo personal|crecimiento personal|business|empresa|econom[ií]a|economics|liderazgo|leadership|management|inteligencia emocional|nacionalismo|liberalismo|comunismo|socialismo|desigualdad|inequality|current events|actualidad)""")),
        Rule("Novela", rx("""\b(novelas?|novels?|fiction|ficci[oó]n|literary|narrativa|narrative|cuentos|short stories|relatos|romance|thriller|mystery|misterio|polic[ií]ac\w*|crime fiction)"""), notIf = rx("""\b(non-?fiction|no ficci[oó]n|science fiction|ciencia ficci[oó]n)\b(?![\s\S]*\b(literary fiction|novela)\b)""")),
        Rule("Clásicos", rx("""\b(cl[aá]sicos?|classics?|classic literature|literatura cl[aá]sica)"""), before = 1965, authors = authors("""Jane Austen""", """Mary Shelley""", """Charles Dickens""", """Le[oó]n Tolst""", """Fi[oó]dor Dostoi""", """Fyodor Dostoevsky""", """Virginia Woolf""", """Gustave Flaubert""", """Miguel de Cervantes""", """Benito P[eé]rez Gald[oó]s""", """Mark Twain""", """Herman Melville""", """Bront[eë]""", """Robert Louis Stevenson""", """Edgar Allan Poe""", """Franz Kafka""", """Marcel Proust""", """James Joyce""", """Victor Hugo""", """Julio Verne""", """Jules Verne""", """Arthur Conan Doyle""", """Hom[eé]ro""", """Homer""", """William Shakespeare""", """Henry James""", """Thomas Mann""", """Oscar Wilde""", """E\.?\s?M\.? Forster""", """Edith Wharton""", """Joseph Conrad""", """Anton Ch[eé]jov""", """Leopoldo Alas""", """Emilia Pardo Baz[aá]n""", """Nathaniel Hawthorne""", """F\.? Scott Fitzgerald""", """Vladimir Nabokov""", """Truman Capote""", """Ana[iï]s Nin""", """Jean Cocteau""", """Ernest Hemingway""", """John Steinbeck""", """William Faulkner""", """J\.?\s?D\.? Salinger""", """George Orwell""", """Aldous Huxley""", """Lev Tolst""", """Tolst[oó]i""", """Charlotte Bront""", """Emily Bront""", """Albert Camus""", """Jean-Paul Sartre""", """Simone de Beauvoir""", """Federico Garc[ií]a Lorca""", """Gabriel Garc[ií]a M[aá]rquez""", """Jorge Luis Borges""", """Julio Cort[aá]zar""", """Mary Shelley""")),
    )

    private val COMBOS = listOf("Novela Gay" to listOf("Novela", "LGTB"), "Ensayo Gay" to listOf("Ensayo", "LGTB"))

    /** The shelves the web ticks by default (Ion's Play Libros shelves). */
    val MY_SHELVES = listOf("Cine", "Clásicos", "Ensayo", "Ensayo Gay", "Fantasía Medieval", "Fantasía", "Fitness", "Historia", "LGTB", "Novela", "Novela Gay", "Pop")
    val ALL_NAMES = RULES.map { it.name } + COMBOS.map { it.first }

    private val FICTION = Regex("fiction|ficci|novel|novela|literat", RegexOption.IGNORE_CASE)
    private val NOVEL_WORD = Regex("\\b(novela|novel|fiction|ficci[oó]n)\\b", RegexOption.IGNORE_CASE)
    private val STARS = Regex("entertainment|performing arts|music|m[uú]sica|actor|actress|actriz|singer|cantante|estrella|stars?\\b|hollywood|celebrit", RegexOption.IGNORE_CASE)

    /** The shelf names that fit one book. */
    fun classify(title: String, author: String?, subjects: String, cats: List<String>, description: String, year: Int?): Set<String> {
        val h = listOf(title, subjects, cats.joinToString(" · "), description.take(700)).joinToString(" \n ")
        val au = author.orEmpty()
        val hit = linkedSetOf<String>()
        for (r in RULES) {
            val byText = r.re.containsMatchIn(h) && r.notIf?.containsMatchIn(h) != true
            val byAuthor = r.authors?.containsMatchIn(au) == true
            val byYear = r.before != null && year != null && year < r.before && FICTION.containsMatchIn(h)
            if (byText || byAuthor || byYear) hit += r.name
        }
        if ("Novela" in hit && "Ensayo" in hit && !NOVEL_WORD.containsMatchIn(cats.joinToString(" ") + " " + subjects)) hit -= "Novela"
        if ("Fantasía Medieval" in hit) hit -= "Fantasía"
        if (listOf("Fantasía Medieval", "Fantasía", "Ciencia ficción").any { it in hit }) hit -= "Novela"
        if ("Biografías y memorias" in hit && "Novela" !in hit) hit += "Ensayo"
        if ("Clásicos" in hit && ("Cine" in hit || "Fitness" in hit)) hit -= "Clásicos"
        if ("Biografías y memorias" in hit && STARS.containsMatchIn(h)) { hit += "Pop"; hit -= "Ensayo" }
        for ((name, all) in COMBOS) if (all.all { it in hit }) { hit += name; hit.removeAll(all.toSet()) }
        return hit
    }

    /** What the library knows about [book] for classifying it. */
    suspend fun classify(context: Context, book: Book): Set<String> {
        val meta = context.container.database.bookMetadataDao().forBook(book.url)
        val subjects = meta.mapNotNull { it.subjects }.joinToString(" · ")
        val desc = meta.firstNotNullOfOrNull { it.description }.orEmpty()
        val published = meta.firstNotNullOfOrNull { it.published }
        val g = if (book.url.startsWith("gdrive:")) LiburuCovers.genres(book.url.removePrefix("gdrive:")) else null
        val cats = g?.optJSONArray("c")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val year = published?.take(4)?.toIntOrNull() ?: g?.optString("y")?.toIntOrNull()
        return classify(book.title, book.author, subjects, cats, desc.ifEmpty { g?.optString("d").orEmpty() }, year)
    }

    /** Shelf name → the books (urls) that belong on it, for the whole library. */
    suspend fun propose(context: Context, books: List<Book>): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        ALL_NAMES.forEach { out[it] = mutableListOf() }
        for (b in books) classify(context, b).forEach { out.getOrPut(it) { mutableListOf() } += b.url }
        return out
    }

    private fun prefs(context: Context) = context.getSharedPreferences("liburu", Context.MODE_PRIVATE)
    // Until chosen here, the shelves the web organises by default.
    fun autoNames(context: Context): Set<String> = prefs(context).getStringSet("autoShelves", null) ?: MY_SHELVES.toSet()

    /** Creates the chosen shelves that are missing and adds their books; never removes any. */
    fun apply(context: Context, proposal: Map<String, List<String>>, chosen: Set<String>): Int {
        val (shelves, membership) = LiburuShelves.load(context)
        val byName = shelves.associateBy { it.name.lowercase() }.toMutableMap()
        val add = HashMap<String, MutableSet<String>>()
        for (name in chosen) {
            val urls = proposal[name].orEmpty()
            if (urls.isEmpty()) continue
            val id = byName[name.lowercase()]?.id ?: LiburuShelves.saveShelf(context, null, name).also {
                byName[name.lowercase()] = LiburuShelves.Shelf(it, name)
            }
            urls.forEach { add.getOrPut(it) { mutableSetOf() } += id }
        }
        var added = 0
        for ((url, ids) in add) {
            val mine = membership[LiburuShelves.keyOf(url)].orEmpty()
            val next = mine + ids
            if (next != mine) { LiburuShelves.setBookShelves(context, url, next); added += (next - mine).size }
        }
        prefs(context).edit().putStringSet("autoShelves", autoNames(context) + chosen).apply()
        LiburuShelves.version.value++
        return added
    }

    /** Puts books that are on no shelf yet into the automatic shelves; returns how many it placed. */
    suspend fun autoAssign(context: Context, books: List<Book>): Int {
        val auto = autoNames(context)
        if (auto.isEmpty()) return 0
        val (_, membership) = LiburuShelves.load(context)
        val seen = prefs(context).getStringSet("autoSeen", emptySet()).orEmpty().toMutableSet()
        val fresh = books.filter { it.url !in seen && membership[LiburuShelves.keyOf(it.url)].isNullOrEmpty() }
        if (fresh.isEmpty()) return 0
        val proposal = propose(context, fresh)
        val placed = apply(context, proposal, auto)
        // A book is looked at once its genres are known, so it is not re-shelved by hand later.
        fresh.filter { b -> !b.url.startsWith("gdrive:") || LiburuCovers.genres(b.url.removePrefix("gdrive:")) != null }
            .forEach { seen += it.url }
        prefs(context).edit().putStringSet("autoSeen", seen).apply()
        return placed
    }
}
