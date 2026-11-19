package com.chmouel.liseur.data.gdrive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.chmouel.liseur.MainActivity
import kotlin.concurrent.thread

/**
 * Receives Google's redirect after sign-in, finishes the token exchange
 * and returns to the settings screen that started it.
 */
class DriveAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriveAuth.init(this)
        val data = intent?.data
        if (data != null) thread(name = "drive-auth") { DriveAuth.finishSignIn(data) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
