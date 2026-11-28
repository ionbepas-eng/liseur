package com.chmouel.liseur.data.gdrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.chmouel.liseur.data.calibre.CredentialCipher
import com.chmouel.liseur.data.remote.RemoteHttp
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Google sign-in for the Drive library (Liburuville).
 *
 * OAuth 2.0 authorization code with PKCE against Google's endpoints, for
 * the Android client registered for this package. No Google Play services
 * and no extra library: the browser shows Google's consent page, the
 * redirect comes back to [DriveAuthActivity], and the code is exchanged
 * here for a refresh token, which is kept encrypted with the same
 * Keystore cipher the server passwords use.
 */
object DriveAuth {
    const val CLIENT_ID = "175081491604-636f4hdfd431llkv538cd91v108qluf7.apps.googleusercontent.com"
    const val REDIRECT_SCHEME = "com.googleusercontent.apps.175081491604-636f4hdfd431llkv538cd91v108qluf7"
    const val REDIRECT_URI = "$REDIRECT_SCHEME:/oauth2redirect"
    const val BASE_URL = "https://www.googleapis.com/drive/v3"
    private const val SCOPES =
        "https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.appdata openid email"
    private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    private const val PREFS = "gdrive_auth"
    private const val KEY_REFRESH = "refresh_cipher"
    private const val KEY_VERIFIER = "pkce_verifier"
    private const val KEY_STATE = "pkce_state"

    private lateinit var app: Context
    private val http: OkHttpClient by lazy { RemoteHttp.default() }

    @Volatile private var accessToken: String? = null
    @Volatile private var accessExpires: Long = 0

    /** Refresh tokens from finished sign-ins, for the connect form to pick up. */
    private val _signedIn = MutableSharedFlow<Result<String>>(replay = 1, extraBufferCapacity = 1)
    val signedIn: SharedFlow<Result<String>> = _signedIn

    /** Forgets a sign-in result once the connect form has used it. */
    fun consumed() = _signedIn.resetReplayCache()

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Opens Google's consent page in the browser. */
    fun startSignIn(context: Context) {
        init(context)
        val verifier = randomToken(64)
        val state = randomToken(24)
        prefs().edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Called off the main thread by [DriveAuthActivity] with the redirect it received. */
    fun finishSignIn(redirect: Uri) {
        val result = runCatching {
            redirect.getQueryParameter("error")?.let { throw IOException(it) }
            val code = redirect.getQueryParameter("code") ?: throw IOException("no code")
            val expected = prefs().getString(KEY_STATE, null)
            if (expected == null || expected != redirect.getQueryParameter("state")) {
                throw IOException("state mismatch")
            }
            val verifier = prefs().getString(KEY_VERIFIER, null) ?: throw IOException("no verifier")
            val json = token(
                FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("code", code)
                    .add("code_verifier", verifier)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", REDIRECT_URI)
                    .build(),
            )
            val refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() }
                ?: throw IOException("no refresh token")
            remember(json)
            saveRefreshToken(refresh)
            refresh
        }
        prefs().edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply()
        _signedIn.tryEmit(result)
    }

    /** The first name of the signed-in account, for the Home greeting. */
    fun saveName(name: String) {
        app.getSharedPreferences("liburu", Context.MODE_PRIVATE).edit().putString("name", name).apply()
    }

    /** Asks Google for the account's first name when the greeting has none yet. Blocking. */
    fun fetchNameBlocking(): String? = runCatching {
        if (refreshToken() == null) return null
        client().newCall(
            Request.Builder().url("$BASE_URL/about?fields=user(displayName)").build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return null
            JSONObject(response.body.string()).optJSONObject("user")?.optString("displayName")
                ?.substringBefore(' ')?.takeIf { it.isNotBlank() }?.also(::saveName)
        }
    }.getOrNull()

    fun saveRefreshToken(token: String) {
        prefs().edit().putString(KEY_REFRESH, CredentialCipher.encrypt(token)).apply()
    }

    fun refreshToken(): String? =
        if (!::app.isInitialized) null
        else prefs().getString(KEY_REFRESH, null)?.let(CredentialCipher::decrypt)

    fun signOut() {
        accessToken = null
        accessExpires = 0
        if (::app.isInitialized) prefs().edit().clear().apply()
        _signedIn.resetReplayCache()
    }

    /** A valid access token, renewing it when needed. Blocking: call off the main thread. */
    @Synchronized
    fun accessTokenBlocking(refresh: String? = refreshToken()): String? {
        val now = System.currentTimeMillis()
        accessToken?.let { if (now < accessExpires - 60_000) return it }
        refresh ?: return null
        val json = runCatching {
            token(
                FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("refresh_token", refresh)
                    .add("grant_type", "refresh_token")
                    .build(),
            )
        }.getOrNull() ?: return null
        return remember(json)
    }

    fun invalidate() {
        accessToken = null
        accessExpires = 0
    }

    private fun remember(json: JSONObject): String? {
        val token = json.optString("access_token").takeIf { it.isNotEmpty() } ?: return null
        accessToken = token
        accessExpires = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
        return token
    }

    /** Liburuville: the HTTP code of the last refused renewal (400 invalid_grant: the sign-in expired), or 0. */
    @Volatile var lastTokenRefusal = 0
        private set

    private fun token(body: FormBody): JSONObject {
        http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { response ->
            val text = response.body.string()
            lastTokenRefusal = if (response.isSuccessful) 0 else response.code
            if (!response.isSuccessful) android.util.Log.w("DriveAuth", "token ${response.code}: $text")
            if (!response.isSuccessful) throw IOException("token ${response.code}: $text")
            return JSONObject(text)
        }
    }

    private fun randomToken(bytes: Int): String {
        val raw = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Signs Google API requests that carry no Authorization header of their own. */
    object Signer : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val host = request.url.host
            if (!host.endsWith("googleapis.com") || request.header("Authorization") != null) {
                return chain.proceed(request)
            }
            val token = accessTokenBlocking() ?: return chain.proceed(request)
            val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
            if (response.code != 401) return response
            response.close()
            invalidate()
            val again = accessTokenBlocking() ?: return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer $again").build())
        }
    }

    fun client(base: OkHttpClient = RemoteHttp.default()): OkHttpClient =
        base.newBuilder().addInterceptor(Signer).build()
}
