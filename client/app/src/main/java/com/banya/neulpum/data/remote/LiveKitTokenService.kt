package com.banya.neulpum.data.remote

import android.util.Log
import com.banya.neulpum.BuildConfig
import com.banya.neulpum.di.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Response from LiveKit Token Server
 */
data class LiveKitTokenResponse(
    val token: String,
    val url: String,
    val roomName: String
)

/**
 * Service for requesting LiveKit access tokens from the token server.
 *
 * The token server validates the user's access token and issues a LiveKit JWT
 * that allows the client to connect to a specific room.
 */
class LiveKitTokenService(
    private val accessToken: String
) {
    companion object {
        private const val TAG = "LiveKitTokenService"

        // Token server endpoint (can be configured)
        // For local development: http://localhost:8081/api/v1/livekit/token
        // For production: ${AppConfig.BASE_HOST}/api/v1/livekit/token
        private const val TOKEN_ENDPOINT = "/api/v1/livekit/token"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Request a LiveKit access token from the token server.
     *
     * @param roomName Unique room identifier
     * @param participantIdentity User's unique identifier
     * @param participantName User's display name
     * @param conversationId Optional conversation ID for context
     * @param tokenServerUrl Optional custom token server URL (for local testing)
     * @return Result containing the token response or error
     */
    suspend fun getToken(
        roomName: String,
        participantIdentity: String,
        participantName: String,
        conversationId: String? = null,
        tokenServerUrl: String? = null
    ): Result<LiveKitTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("room_name", roomName)
                put("participant_identity", participantIdentity)
                put("participant_name", participantName)
                conversationId?.let { put("conversation_id", it) }
            }

            // Use custom URL, BuildConfig value, or fallback to AppConfig
            val baseUrl = tokenServerUrl
                ?: BuildConfig.LIVEKIT_TOKEN_SERVER_URL.ifEmpty { AppConfig.BASE_HOST }
            val url = "${baseUrl}${TOKEN_ENDPOINT}"

            Log.d(TAG, "Requesting token from: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Token request failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    Exception("Token request failed: ${response.code}")
                )
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response from server"))
            }

            val responseJson = JSONObject(body)

            Result.success(
                LiveKitTokenResponse(
                    token = responseJson.getString("token"),
                    url = responseJson.getString("url"),
                    roomName = responseJson.getString("room_name")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token request error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
