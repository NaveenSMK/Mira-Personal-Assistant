package com.example.voicefirst

import android.app.Activity
import android.content.Intent
import android.util.Base64
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.io.IOException

data class EmailSummary(
    val id: String,
    val threadId: String?,
    val from: String,
    val subject: String,
    val date: String,
    val snippet: String
)

class GmailConnector(private val activity: Activity) {
    companion object {
        const val REQUEST_AUTH = 7012
        private const val READ_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val COMPOSE_SCOPE = "https://www.googleapis.com/auth/gmail.compose"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    @Volatile private var accessToken: String? = null

    fun beginAuthorization() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(READ_SCOPE), Scope(COMPOSE_SCOPE)))
            .build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    try {
                        activity.startIntentSenderForResult(
                            result.pendingIntent!!.intentSender,
                            REQUEST_AUTH, null, 0, 0, 0
                        )
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            activity,
                            "Could not start Google authorization: ${e.message ?: "unknown error"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    accessToken = result.accessToken
                }
            }
            .addOnFailureListener { err ->
                android.widget.Toast.makeText(activity, "Gmail authorization failed: ${err.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    fun finishAuthorization(data: Intent?): Boolean {
        val result = AuthorizationResult.fromIntent(data)
        val token = result?.accessToken
        if (token.isNullOrBlank()) return false
        accessToken = token
        return true
    }

    fun isConnected(): Boolean = !accessToken.isNullOrBlank()

    suspend fun searchUnread(maxResults: Int = 10): List<EmailSummary> = withContext(Dispatchers.IO) {
        val token = requireToken()
        val listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${java.net.URLEncoder.encode("is:unread newer_than:7d", "UTF-8")}&maxResults=${maxResults.coerceIn(1, 20)}"
        val listJson = getJson(listUrl, token)
        val messages = listJson.optJSONArray("messages") ?: return@withContext emptyList()
        val out = mutableListOf<EmailSummary>()
        for (i in 0 until messages.length()) {
            val id = messages.getJSONObject(i).optString("id")
            if (id.isBlank()) continue
            val msg = getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date", token)
            val headers = msg.optJSONObject("payload")?.optJSONArray("headers")
            var from = ""
            var subject = "(no subject)"
            var date = ""
            if (headers != null) for (j in 0 until headers.length()) {
                val h = headers.getJSONObject(j)
                when (h.optString("name").lowercase()) {
                    "from" -> from = h.optString("value")
                    "subject" -> subject = h.optString("value")
                    "date" -> date = h.optString("value")
                }
            }
            out += EmailSummary(id, msg.optString("threadId"), from, subject, date, msg.optString("snippet"))
        }
        out
    }

    suspend fun createDraft(to: String, subject: String, body: String): String = withContext(Dispatchers.IO) {
        require(to.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(to) && !to.contains('\r') && !to.contains('\n')) {
            "Enter a valid recipient email address."
        }
        require(subject.length <= 200 && body.length <= 20_000) { "Subject must be at most 200 characters and body at most 20,000 characters." }
        val token = requireToken()
        val safeSubject = subject.replace("\r", " ").replace("\n", " ")
        val mime = "To: $to\r\nSubject: $safeSubject\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
        val raw = Base64.encodeToString(mime.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payload = JSONObject().put("message", JSONObject().put("raw", raw))
        postJson("https://gmail.googleapis.com/gmail/v1/users/me/drafts", token, payload).optJSONObject("message")?.optString("id")
            ?: "Draft created"
    }

    private fun requireToken(): String = accessToken ?: throw IllegalStateException("Connect Gmail first.")

    private fun getJson(url: String, token: String): JSONObject {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Gmail API ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    private fun postJson(url: String, token: String, json: JSONObject): JSONObject {
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Gmail API ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }
}
