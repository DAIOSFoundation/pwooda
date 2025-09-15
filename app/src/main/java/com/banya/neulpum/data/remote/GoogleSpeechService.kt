package com.banya.neulpum.data.remote

import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class GoogleSpeechService(
    private val context: Context,
    private val apiKey: String
) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var silenceJob: Job? = null
    private var lastPartial: String = ""
    private var finalSent: Boolean = false
    private var finalCallback: ((String) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    private val silenceWindowMs: Long = 4000 // 무음 대기 4초로 변경
    
    companion object {
        private const val TAG = "GoogleSpeechService"
        private const val GOOGLE_TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
    }
    
    // 음성 인식 시작
    fun startSpeechRecognition(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        finalCallback = onFinalResult
        errorCallback = onError
        finalSent = false
        lastPartial = ""
        try {
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식할 수 없습니다"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간이 초과되었습니다"
                        SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류가 발생했습니다"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 시간이 초과되었습니다"
                        else -> "음성 인식 오류가 발생했습니다"
                    }
                    errorCallback?.invoke(errorMessage)
                }
                
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        if (!finalSent) {
                            finalSent = true
                            val finalResult = matches[0]
                            finalCallback?.invoke(finalResult)
                        }
                    }
                }
                
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partialResult = matches[0]
                        lastPartial = partialResult
                        onPartialResult(partialResult)
                        // 재시작: 무음 윈도우 내 더 이상의 partial이 없으면 자동 종료/전송
                        silenceJob?.cancel()
                        silenceJob = scope.launch {
                            delay(silenceWindowMs)
                            finalizeFromSilence()
                        }
                    }
                }
                
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            }
            
            speechRecognizer.setRecognitionListener(listener)
            speechRecognizer.startListening(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Speech recognition error: ${e.message}")
            errorCallback?.invoke("음성 인식 초기화 오류: ${e.message}")
        }
    }
    
    // 음성 인식 중지
    fun stopSpeechRecognition() {
        try {
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition: ${e.message}")
        }
    }

    fun stopAndFinalize() {
        try {
            speechRecognizer.stopListening()
        } catch (_: Exception) {}
        finalizeFromSilence()
    }

    private fun finalizeFromSilence() {
        if (!finalSent) {
            finalSent = true
            val text = lastPartial
            if (text.isNotBlank()) {
                finalCallback?.invoke(text)
            } else {
                // nothing recognized; still notify error or ignore
                errorCallback?.invoke("음성 인식이 종료되었습니다")
            }
        }
    }
    
    // Google TTS API로 음성 합성
    suspend fun synthesizeSpeech(
        text: String,
        onAudioData: (ByteArray) -> Unit
    ) {
        try {
            val requestBody = JSONObject().apply {
                put("input", JSONObject().put("text", text))
                put("voice", JSONObject().apply {
                    put("languageCode", "ko-KR")
                    put("name", "ko-KR-Neural2-A")
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                    put("speakingRate", 1.0)
                    put("pitch", 0.0)
                })
            }.toString()
            
            val request = Request.Builder()
                .url("$GOOGLE_TTS_URL?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "")
                val audioContent = jsonResponse.optString("audioContent", "")
                
                if (audioContent.isNotEmpty()) {
                    val audioData = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                    onAudioData(audioData)
                }
            } else {
                Log.e(TAG, "TTS API error: ${response.code}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "TTS error: ${e.message}")
        }
    }
    
    // 리소스 정리
    fun cleanup() {
        try {
            speechRecognizer.destroy()
            client.dispatcher.executorService.shutdown()
            silenceJob?.cancel()
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}
