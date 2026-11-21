package com.chmouel.liseur.audio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.chmouel.liseur.data.gdrive.DriveAuth
import kotlinx.coroutines.launch

/**
 * Plays an audiobook's tracks straight from Drive (signed with the account's
 * token), with the lock screen and notification controls of any player.
 */
class AudiobookService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val data = OkHttpDataSource.Factory(DriveAuth.client())
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(data))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000).setSeekForwardIncrementMs(30_000)
            .build()
        session = MediaSession.Builder(this, player).setId("liburu-audiobook").build()
        player.addListener(Tracker(player))
        handler.postDelayed(tick, 15_000)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            session?.player?.let { if (it.isPlaying) progress(it) }
            handler.postDelayed(this, 15_000)
        }
    }

    private fun bookOf(p: androidx.media3.common.Player): LiburuAudiobooks.Audiobook? {
        val id = p.currentMediaItem?.mediaId ?: return null
        return LiburuAudiobooks.list.value.firstOrNull { b -> b.tracks.any { it.id == id } }
    }

    private fun fraction(p: androidx.media3.common.Player, b: LiburuAudiobooks.Audiobook): Float {
        val sizes = b.tracks.map { it.size.coerceAtLeast(1) }
        val i = p.currentMediaItemIndex.coerceIn(0, sizes.size - 1)
        val inTrack = if (p.duration > 0) sizes[i] * p.currentPosition / p.duration else 0
        return ((sizes.take(i).sum() + inTrack).toFloat() / sizes.sum()).coerceIn(0f, 1f)
    }

    /** Where you are: kept for the player, and as the book's progress for the shelf and the other devices. */
    private fun progress(p: androidx.media3.common.Player) {
        val b = bookOf(p) ?: return
        val f = fraction(p, b)
        LiburuAudiobooks.savePosition(this, b.id, p.currentMediaItemIndex, p.currentPosition, f)
        val url = "gdrive:" + b.id.removePrefix("a:")
        val dao = (application as com.chmouel.liseur.LiseurApplication).container.database.readingProgressDao()
        kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val row = dao.get(url)
            dao.upsert(row?.copy(totalProgression = f.toDouble(), updatedAt = now, readAt = now)
                ?: com.chmouel.liseur.data.db.ReadingProgress(bookUrl = url, locatorJson = "{}", totalProgression = f.toDouble(), updatedAt = now, readAt = now))
        }
    }

    /** Listening counts as reading: each stretch is a session, the last track's end finishes the book. */
    private inner class Tracker(val p: androidx.media3.common.Player) : androidx.media3.common.Player.Listener {
        var since = 0L
        var fromFraction = 0f
        var book: LiburuAudiobooks.Audiobook? = null
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                book = bookOf(p); since = System.currentTimeMillis(); fromFraction = book?.let { fraction(p, it) } ?: 0f
            } else close()
        }
        fun close() {
            val b = book ?: return
            val end = System.currentTimeMillis()
            progress(p)
            if (since > 0 && end - since >= 20_000) {
                val s = com.chmouel.liseur.data.db.ReadingSession(
                    bookUrl = "gdrive:" + b.id.removePrefix("a:"), startedAt = since, endedAt = end, lastCheckpointAt = end,
                    durationMs = end - since, startProgression = fromFraction.toDouble(), endProgression = fraction(p, b).toDouble(),
                )
                val db = (application as com.chmouel.liseur.LiseurApplication).container.database
                kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { db.readingSessionDao().insert(s) } }
            }
            since = 0
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == androidx.media3.common.Player.STATE_ENDED) {
                val b = bookOf(p) ?: book ?: return
                close()
                val c = (application as com.chmouel.liseur.LiseurApplication).container
                kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { c.finishedState.setFinished("gdrive:" + b.id.removePrefix("a:"), true) } }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        session?.player?.let { progress(it) }
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
