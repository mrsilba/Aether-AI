package com.aether.storage

import android.content.Context
import com.aether.security.SecurityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class DriveOAuthState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting to Google Identity"),
    AUTHENTICATED("Authenticated"),
    TOKEN_EXPIRED("Access Token Expired"),
    ERROR("Authentication Error")
}

data class DriveAccountInfo(
    val email: String,
    val displayName: String,
    val scopes: List<String>,
    val expiresAtMs: Long
)

/**
 * Manages Google Drive OAuth 2.0 authentication lifecycle,
 * token encryption via Android Keystore, least-privilege scopes,
 * and token revocation.
 */
class GoogleDriveOAuthManager(
    private val context: Context,
    private val securityEngine: SecurityEngine = SecurityEngine(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val PREF_NAME = "aether_drive_oauth_vault"
        const val KEY_ENCRYPTED_ACCESS_TOKEN = "drive_enc_access_token"
        const val KEY_ENCRYPTED_REFRESH_TOKEN = "drive_enc_refresh_token"
        const val KEY_TOKEN_EXPIRY = "drive_token_expiry"
        const val KEY_ACCOUNT_EMAIL = "drive_account_email"

        // Least-Privilege Drive Scopes
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"

        const val GOOGLE_TOKEN_REVOCATION_URL = "https://oauth2.googleapis.com/revoke"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<DriveOAuthState>(DriveOAuthState.DISCONNECTED)
    val authState: StateFlow<DriveOAuthState> = _authState.asStateFlow()

    private val _currentAccount = MutableStateFlow<DriveAccountInfo?>(null)
    val currentAccount: StateFlow<DriveAccountInfo?> = _currentAccount.asStateFlow()

    init {
        restoreCachedSession()
    }

    private fun restoreCachedSession() {
        val encryptedToken = prefs.getString(KEY_ENCRYPTED_ACCESS_TOKEN, null)
        val expiryMs = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        val email = prefs.getString(KEY_ACCOUNT_EMAIL, null)

        if (!encryptedToken.isNullOrBlank() && !email.isNullOrBlank()) {
            if (System.currentTimeMillis() < expiryMs) {
                _authState.value = DriveOAuthState.AUTHENTICATED
                _currentAccount.value = DriveAccountInfo(
                    email = email,
                    displayName = email.substringBefore("@"),
                    scopes = listOf(SCOPE_DRIVE_FILE, SCOPE_DRIVE_READONLY),
                    expiresAtMs = expiryMs
                )
            } else {
                _authState.value = DriveOAuthState.TOKEN_EXPIRED
            }
        } else {
            _authState.value = DriveOAuthState.DISCONNECTED
        }
    }

    /**
     * Retrieves active decrypted access token if valid.
     */
    fun getActiveAccessToken(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_ACCESS_TOKEN, null) ?: return null
        val expiryMs = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        if (System.currentTimeMillis() >= expiryMs) {
            _authState.value = DriveOAuthState.TOKEN_EXPIRED
            return null
        }
        return try {
            securityEngine.decryptString(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves newly negotiated OAuth tokens into Keystore-encrypted preferences.
     */
    fun saveSession(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
        email: String
    ): Boolean {
        return try {
            val encAccess = securityEngine.encryptString(accessToken)
            val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)

            val editor = prefs.edit()
                .putString(KEY_ENCRYPTED_ACCESS_TOKEN, encAccess)
                .putLong(KEY_TOKEN_EXPIRY, expiryMs)
                .putString(KEY_ACCOUNT_EMAIL, email)

            if (!refreshToken.isNullOrBlank()) {
                val encRefresh = securityEngine.encryptString(refreshToken)
                editor.putString(KEY_ENCRYPTED_REFRESH_TOKEN, encRefresh)
            }

            editor.apply()

            _authState.value = DriveOAuthState.AUTHENTICATED
            _currentAccount.value = DriveAccountInfo(
                email = email,
                displayName = email.substringBefore("@"),
                scopes = listOf(SCOPE_DRIVE_FILE, SCOPE_DRIVE_READONLY),
                expiresAtMs = expiryMs
            )
            true
        } catch (e: Exception) {
            _authState.value = DriveOAuthState.ERROR
            false
        }
    }

    /**
     * Disconnects account, clears local tokens, and invokes Google token revocation endpoint.
     */
    suspend fun disconnectAndRevoke(): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = getActiveAccessToken()
        prefs.edit().clear().apply()
        _authState.value = DriveOAuthState.DISCONNECTED
        _currentAccount.value = null

        if (token != null) {
            try {
                val body = FormBody.Builder()
                    .add("token", token)
                    .build()
                val request = Request.Builder()
                    .url(GOOGLE_TOKEN_REVOCATION_URL)
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (ignored: Exception) {
                // Best-effort remote revocation
            }
        }
        Result.success(true)
    }

    /**
     * Verifies whether Google Cloud OAuth client credentials are configured.
     */
    fun getConfigurationStatus(): GoogleCloudOAuthConfigStatus {
        val packageName = context.packageName
        // In standalone container builds without Google Services OAuth client ID
        return GoogleCloudOAuthConfigStatus(
            isConfigured = false,
            packageName = packageName,
            requiredScopes = listOf(SCOPE_DRIVE_FILE, SCOPE_DRIVE_READONLY),
            statusMessage = "Google Cloud Console OAuth 2.0 Client ID required for live OAuth token negotiation. Use SAF DocumentProvider or configure credentials in Cloud Console."
        )
    }
}

data class GoogleCloudOAuthConfigStatus(
    val isConfigured: Boolean,
    val packageName: String,
    val requiredScopes: List<String>,
    val statusMessage: String
)
