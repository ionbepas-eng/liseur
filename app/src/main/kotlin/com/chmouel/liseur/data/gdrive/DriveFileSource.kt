package com.chmouel.liseur.data.gdrive

import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.remote.FileSource
import com.chmouel.liseur.data.remote.RemoteCredentials
import com.chmouel.liseur.data.remote.RemoteHttp
import okhttp3.Request

/** A book's file in Google Drive, fetched with the signed-in account's token. */
class DriveFileSource : FileSource {
    override fun downloadRequest(
        server: com.chmouel.liseur.data.db.RemoteServer,
        credentials: RemoteCredentials,
        book: Book,
    ): Request.Builder? {
        val id = book.remoteUuid ?: return null
        return Request.Builder().url("${DriveAuth.BASE_URL}/files/$id?alt=media&acknowledgeAbuse=false")
    }

    override fun downloadHttp(): RemoteHttp = RemoteHttp(DriveAuth.client(RemoteHttp.forDownloads()))
}
