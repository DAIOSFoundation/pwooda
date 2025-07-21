package com.banya.pwooda.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.concurrent.TimeUnit

data class TTSRequest(
    val input: TTSInput,
    val voice: TTSVoice,
    val audioConfig: TTSAudioConfig
)

data class TTSInput(
    val text: String
)

data class TTSVoice(
    val languageCode: String = "ko-KR",
    val name: String = "ko-KR-Chirp3-HD-Zephyr"
)

data class TTSAudioConfig(
    val audioEncoding: String = "MP3",
    val speakingRate: Double = 1.0,
    val pitch: Double = 0.0,
    val volumeGainDb: Double = 0.0
)

data class TTSResponse(
    val audioContent: String
)

data class ServiceAccountKey(
    val type: String,
    val project_id: String,
    val private_key_id: String,
    val private_key: String,
    val client_email: String,
    val client_id: String,
    val auth_uri: String,
    val token_uri: String,
    val auth_provider_x509_cert_url: String,
    val client_x509_cert_url: String,
    val universe_domain: String
)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

class GoogleCloudTTSService(private val context: Context) {
    
    // HTTP 클라이언트 최적화
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS) // 연결 타임아웃 증가
        .readTimeout(30, TimeUnit.SECONDS) // 읽기 타임아웃 증가
        .writeTimeout(30, TimeUnit.SECONDS) // 쓰기 타임아웃 증가
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES)) // 연결 풀 크기 증가
        .retryOnConnectionFailure(true) // 연결 실패 시 재시도
        .build()
    
    private val gson = Gson()
    private val mediaPlayer = MediaPlayer()
    
    // 토큰 캐싱
    private var cachedAccessToken: String? = null
    private var tokenExpiryTime: Long = 0
    
    // Google Cloud TTS API 엔드포인트
    private val ttsApiUrl = "https://texttospeech.googleapis.com/v1/text:synthesize"
    
    suspend fun synthesizeSpeech(text: String, onStart: () -> Unit, onComplete: () -> Unit, onError: (String) -> Unit = {}) {
        Log.d("GoogleCloudTTS", "synthesizeSpeech 시작: $text")
        Log.d("GoogleCloudTTS", "synthesizeSpeech - 텍스트 길이: ${text.length}")
        Log.d("GoogleCloudTTS", "synthesizeSpeech - 콜백 함수 확인: onStart=${onStart != null}, onComplete=${onComplete != null}, onError=${onError != null}")
        
        try {
            withContext(Dispatchers.IO) {
                Log.d("GoogleCloudTTS", "IO 스레드에서 실행 시작")
                
                // 액세스 토큰 가져오기
                Log.d("GoogleCloudTTS", "액세스 토큰 요청 시작")
                val accessToken = getValidAccessToken()
                if (accessToken != null) {
                    Log.d("GoogleCloudTTS", "액세스 토큰 획득 성공: ${accessToken.take(20)}...")
                    
                    // TTS 요청 생성
                    val ttsRequest = TTSRequest(
                        input = TTSInput(text = text),
                        voice = TTSVoice(),
                        audioConfig = TTSAudioConfig()
                    )
                    
                    Log.d("GoogleCloudTTS", "TTS 요청 생성 완료: ${gson.toJson(ttsRequest)}")
                    
                    val requestBody = gson.toJson(ttsRequest).toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(ttsApiUrl)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    
                    Log.d("GoogleCloudTTS", "TTS API 호출 시작")
                    client.newCall(request).execute().use { response ->
                        Log.d("GoogleCloudTTS", "TTS API 응답 받음: ${response.code}")
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                Log.d("GoogleCloudTTS", "TTS 응답 본문 길이: ${responseBody.length}")
                                val ttsResponse = gson.fromJson(responseBody, TTSResponse::class.java)
                                
                                // Base64 디코딩 및 메모리에서 직접 재생
                                val audioData = android.util.Base64.decode(ttsResponse.audioContent, android.util.Base64.DEFAULT)
                                Log.d("GoogleCloudTTS", "오디오 데이터 디코딩 완료: ${audioData.size} bytes")
                                playAudioFromMemory(audioData, onStart, onComplete, onError)
                            } else {
                                val errorMessage = "TTS 응답이 비어있습니다"
                                Log.e("GoogleCloudTTS", errorMessage)
                                onError(errorMessage)
                            }
                        } else {
                            val errorMessage = "API 호출 실패: ${response.code} - ${response.body?.string()}"
                            Log.e("GoogleCloudTTS", errorMessage)
                            onError(errorMessage)
                        }
                    }
                } else {
                    val errorMessage = "액세스 토큰을 가져올 수 없습니다"
                    Log.e("GoogleCloudTTS", errorMessage)
                    onError(errorMessage)
                }
            }
        } catch (e: Exception) {
            val errorMessage = "TTS 처리 중 오류 발생: ${e.message}"
            Log.e("GoogleCloudTTS", errorMessage, e)
            onError(errorMessage)
        }
    }
    
    private suspend fun getValidAccessToken(): String? {
        val currentTime = System.currentTimeMillis() / 1000
        
        // 캐시된 토큰이 유효한지 확인 (10분 여유 시간으로 증가)
        if (cachedAccessToken != null && currentTime < (tokenExpiryTime - 600)) {
            Log.d("GoogleCloudTTS", "캐시된 토큰 사용")
            return cachedAccessToken
        }
        
        Log.d("GoogleCloudTTS", "새로운 토큰 요청")
        return try {
            val inputStream = context.assets.open("google_tts_key.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            
            val jsonString = String(buffer)
            val serviceAccountKey = gson.fromJson(jsonString, ServiceAccountKey::class.java)
            
            // JWT 토큰 생성 및 액세스 토큰 요청
            val newToken = generateAccessToken(serviceAccountKey)
            if (newToken != null) {
                cachedAccessToken = newToken
                tokenExpiryTime = currentTime + 3600 // 1시간 후 만료
                Log.d("GoogleCloudTTS", "새로운 토큰 캐시됨")
            }
            newToken
        } catch (e: Exception) {
            Log.e("GoogleCloudTTS", "서비스 계정 키 파일 읽기 실패", e)
            null
        }
    }
    
    private fun generateAccessToken(serviceAccountKey: ServiceAccountKey): String? {
        return try {
            // JWT 토큰 생성
            val now = System.currentTimeMillis() / 1000
            val jwtHeader = mapOf(
                "alg" to "RS256",
                "typ" to "JWT"
            )
            
            val jwtPayload = mapOf(
                "iss" to serviceAccountKey.client_email,
                "scope" to "https://www.googleapis.com/auth/cloud-platform",
                "aud" to "https://oauth2.googleapis.com/token",
                "exp" to (now + 3600), // 1시간 후 만료
                "iat" to now
            )
            
            val jwtHeaderBase64 = android.util.Base64.encodeToString(
                gson.toJson(jwtHeader).toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            
            val jwtPayloadBase64 = android.util.Base64.encodeToString(
                gson.toJson(jwtPayload).toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            
            val jwtSignature = signJWT("$jwtHeaderBase64.$jwtPayloadBase64", serviceAccountKey.private_key)
            val jwtToken = "$jwtHeaderBase64.$jwtPayloadBase64.$jwtSignature"
            
            // 액세스 토큰 요청
            requestAccessToken(jwtToken)
        } catch (e: Exception) {
            Log.e("GoogleCloudTTS", "JWT 토큰 생성 실패", e)
            null
        }
    }
    
    private fun signJWT(data: String, privateKeyString: String): String {
        try {
            // 개인키 파싱
            val privateKeyPEM = privateKeyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
            
            val privateKeyBytes = android.util.Base64.decode(privateKeyPEM, android.util.Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)
            
            // RSA 서명 생성
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(data.toByteArray())
            val signatureBytes = signature.sign()
            
            return android.util.Base64.encodeToString(
                signatureBytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        } catch (e: Exception) {
            Log.e("GoogleCloudTTS", "JWT 서명 생성 실패", e)
            throw e
        }
    }
    
    private fun requestAccessToken(jwtToken: String): String? {
        val tokenRequestBody = mapOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion" to jwtToken
        )
        
        val requestBody = gson.toJson(tokenRequestBody).toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(requestBody)
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val tokenResponse = gson.fromJson(responseBody, TokenResponse::class.java)
                        tokenResponse.access_token
                    } else null
                } else {
                    Log.e("GoogleCloudTTS", "토큰 요청 실패: ${response.code} - ${response.body?.string()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleCloudTTS", "액세스 토큰 요청 실패", e)
            null
        }
    }
    
    // 메모리에서 직접 오디오 재생 (파일 생성 없이)
    private fun playAudioFromMemory(audioData: ByteArray, onStart: () -> Unit, onComplete: () -> Unit, onError: (String) -> Unit) {
        Log.d("GoogleCloudTTS", "playAudioFromMemory 시작: ${audioData.size} bytes")
        var tempFile: File? = null
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var timeoutRunnable: Runnable? = null
        
        try {
            mediaPlayer.reset()
            Log.d("GoogleCloudTTS", "MediaPlayer 리셋 완료")
            
            tempFile = File.createTempFile("tts_temp_", ".mp3", context.cacheDir)
            Log.d("GoogleCloudTTS", "임시 파일 생성: ${tempFile.absolutePath}")
            
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioData)
            }
            Log.d("GoogleCloudTTS", "오디오 데이터를 임시 파일에 쓰기 완료")
            
            mediaPlayer.setDataSource(tempFile.absolutePath)
            Log.d("GoogleCloudTTS", "MediaPlayer 데이터 소스 설정 완료")
            
            timeoutRunnable = Runnable {
                Log.e("GoogleCloudTTS", "MediaPlayer 준비 타임아웃")
                onError("MediaPlayer 준비 타임아웃")
                onComplete()
                tempFile?.delete()
                tempFile = null
            }
            timeoutHandler.postDelayed(timeoutRunnable, 20000)
            
            mediaPlayer.setOnPreparedListener {
                Log.d("GoogleCloudTTS", "MediaPlayer 준비 완료, 재생 시작")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                onStart()
                it.start()
            }
            
            mediaPlayer.setOnCompletionListener {
                Log.d("GoogleCloudTTS", "MediaPlayer 재생 완료")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                onComplete()
                tempFile?.delete()
                tempFile = null
                Log.d("GoogleCloudTTS", "임시 파일 삭제 완료")
            }
            
            mediaPlayer.setOnErrorListener { _, what, extra ->
                val errorMessage = "오디오 재생 오류: what=$what, extra=$extra"
                Log.e("GoogleCloudTTS", errorMessage)
                timeoutHandler.removeCallbacks(timeoutRunnable)
                onError(errorMessage)
                onComplete()
                tempFile?.delete()
                tempFile = null
                Log.d("GoogleCloudTTS", "오류 발생으로 임시 파일 삭제")
                true
            }
            
            mediaPlayer.prepareAsync() // 비동기 준비 호출
            Log.d("GoogleCloudTTS", "MediaPlayer prepareAsync 호출")
            
        } catch (e: Exception) {
            val errorMessage = "오디오 재생 준비 실패: ${e.message}"
            Log.e("GoogleCloudTTS", errorMessage, e)
            onError(errorMessage)
            onComplete()
            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) } // 예외 발생 시에도 타임아웃 콜백 제거
            tempFile?.delete()
            tempFile = null
        }
    }
    
    fun stop() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
    }
    
    fun release() {
        mediaPlayer.release()
    }
}