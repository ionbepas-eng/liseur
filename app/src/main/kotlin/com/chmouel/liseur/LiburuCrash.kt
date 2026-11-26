package com.chmouel.liseur

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Liburuville: when the app falls over, the reason is written down, and the next time it opens
 * it offers to share it — there is no other way to learn why a phone far away crashed.
 */
object LiburuCrash {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(error)
                file(app).writeText(
                    "Liburuville ${BuildConfig.VERSION_NAME} · ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}\n" +
                        "${java.util.Date()}\nhilo: ${thread.name}\n\n$trace",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun offer(activity: Activity) {
        val f = file(activity)
        if (!f.exists()) return
        val text = runCatching { f.readText() }.getOrDefault("")
        f.delete()
        if (text.isBlank()) return
        AlertDialog.Builder(activity)
            .setTitle("La app se cerró la última vez")
            .setMessage("Puedes enviar el informe del error para arreglarlo.\n\n" + text.lineSequence().take(8).joinToString("\n"))
            .setPositiveButton("Compartir") { _, _ ->
                activity.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                        "Compartir error",
                    ),
                )
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
