package com.chmouel.liseur.ui.library

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** Which books of the library are comics (CBZ), for the Cómics section. */
object LiburuComics {
    private var prefs: SharedPreferences? = null
    val ids = MutableStateFlow<Set<String>>(emptySet())

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("liburu", Context.MODE_PRIVATE)
        prefs = p
        ids.value = p.getStringSet("comics", emptySet()).orEmpty()
    }

    /** [url] is a book's library URL (`gdrive:<id>` for Drive). */
    fun mark(url: String) {
        if (url in ids.value) return
        val next = ids.value + url
        ids.value = next
        prefs?.edit()?.putStringSet("comics", next)?.apply()
    }

    fun isComic(url: String) = url in ids.value
}
