package com.chmouel.liseur.data.gdrive

import com.chmouel.liseur.data.remote.RemoteCredentials
import com.chmouel.liseur.data.remote.ServerCapabilities
import com.chmouel.liseur.data.remote.ServerSetup
import com.chmouel.liseur.data.remote.SetupFailure
import com.chmouel.liseur.data.remote.SetupResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * "Connects" Google Drive: the refresh token from the sign-in arrives as
 * the credential, and the account it belongs to is asked who it is.
 */
class DriveSetupClient(private val http: OkHttpClient = DriveAuth.client()) : ServerSetup {
    override suspend fun connect(
        rawUrl: String,
        credentials: RemoteCredentials,
        allowHttp: Boolean,
    ): SetupResult = withContext(Dispatchers.IO) {
        val refresh = (credentials as? RemoteCredentials.ApiKey)?.key
            ?: return@withContext SetupResult.Failure(SetupFailure.SignInRequired)
        DriveAuth.invalidate()
        val token = DriveAuth.accessTokenBlocking(refresh)
            ?: return@withContext SetupResult.Failure(SetupFailure.BadCredentials)
        DriveAuth.saveRefreshToken(refresh)
        try {
            val request = Request.Builder()
                .url("${DriveAuth.BASE_URL}/about?fields=user(emailAddress,displayName)")
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return@withContext SetupResult.Failure(SetupFailure.InsufficientScopes)
                }
                if (!response.isSuccessful) {
                    return@withContext SetupResult.Failure(SetupFailure.Unreachable("Drive ${response.code}", false))
                }
                val user = JSONObject(response.body.string()).optJSONObject("user")
                val email = user?.optString("emailAddress").orEmpty()
                SetupResult.Success(
                    ServerCapabilities(
                        baseUrl = DriveAuth.BASE_URL,
                        canDownload = true,
                        accountId = email.ifEmpty { null },
                        displayName = email.ifEmpty { user?.optString("displayName").orEmpty() },
                    ),
                )
            }
        } catch (e: IOException) {
            SetupResult.Failure(SetupFailure.Unreachable(e.message ?: "Drive", false))
        }
    }
}
