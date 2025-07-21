package com.banya.pwooda.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class AndroidTTSService(private val context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var selectedVoice: Voice? = null
    
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                textToSpeech = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = textToSpeech?.setLanguage(Locale.KOREAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("AndroidTTS", "한국어가 지원되지 않습니다")
                            isInitialized = false
                        } else {
                            Log.d("AndroidTTS", "TTS 초기화 성공")
                            isInitialized = true
                            
                            // 사용 가능한 한국어 음성 목록 출력
                            logAvailableVoices()
                            
                            // 최적의 한국어 여성 음성 선택
                            selectBestKoreanVoice()
                            
                            // 음성 설정 최적화
                            textToSpeech?.setSpeechRate(1.0f) // 속도
                            textToSpeech?.setPitch(1.0f) // 음조
                        }
                    } else {
                        Log.e("AndroidTTS", "TTS 초기화 실패")
                        isInitialized = false
                    }
                }
                
                // 초기화 완료까지 대기
                var attempts = 0
                while (!isInitialized && attempts < 50) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
                
                isInitialized
            } catch (e: Exception) {
                Log.e("AndroidTTS", "TTS 초기화 중 오류 발생", e)
                false
            }
        }
    }
    
    private fun logAvailableVoices() {
        val voices = textToSpeech?.voices
        Log.d("AndroidTTS", "사용 가능한 음성 목록:")
        
        voices?.forEach { voice ->
            if (voice.locale.language == "ko") {
                Log.d("AndroidTTS", "한국어 음성: ${voice.name} (${voice.locale}) - 품질: ${voice.quality}")
            }
        }
    }
    
    private fun selectBestKoreanVoice() {
        val voices = textToSpeech?.voices
        val koreanVoices = voices?.filter { it.locale.language == "ko" }
        
        if (koreanVoices.isNullOrEmpty()) {
            Log.w("AndroidTTS", "한국어 음성을 찾을 수 없습니다")
            return
        }
        
        // 우선순위: 여성 음성 > 고품질 > 기본 음성
        val preferredVoice = koreanVoices.find { voice ->
            voice.name.contains("female", ignoreCase = true) || 
            voice.name.contains("여성", ignoreCase = true) ||
            voice.name.contains("female", ignoreCase = true)
        } ?: koreanVoices.find { voice ->
            voice.quality == Voice.QUALITY_HIGH
        } ?: koreanVoices.first()
        
        selectedVoice = preferredVoice
        textToSpeech?.voice = preferredVoice
        
        Log.d("AndroidTTS", "선택된 음성: ${preferredVoice.name} (${preferredVoice.locale})")
    }
    
    fun getAvailableKoreanVoices(): List<Voice> {
        val voices = textToSpeech?.voices
        return voices?.filter { it.locale.language == "ko" } ?: emptyList()
    }
    
    fun setVoice(voice: Voice): Boolean {
        return try {
            textToSpeech?.voice = voice
            selectedVoice = voice
            Log.d("AndroidTTS", "음성 변경: ${voice.name}")
            true
        } catch (e: Exception) {
            Log.e("AndroidTTS", "음성 변경 실패", e)
            false
        }
    }
    
    fun getCurrentVoice(): Voice? {
        return selectedVoice
    }
    
    suspend fun synthesizeSpeech(
        text: String, 
        onStart: () -> Unit, 
        onComplete: () -> Unit, 
        onError: (String) -> Unit = {}
    ) {
        Log.d("AndroidTTS", "synthesizeSpeech 시작: $text")
        
        if (!isInitialized) {
            val initResult = initialize()
            if (!initResult) {
                onError("TTS 초기화 실패")
                return
            }
        }
        
        try {
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("AndroidTTS", "TTS 재생 시작: $utteranceId")
                    onStart()
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d("AndroidTTS", "TTS 재생 완료: $utteranceId")
                    onComplete()
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e("AndroidTTS", "TTS 재생 오류: $utteranceId")
                    onError("TTS 재생 오류")
                    onComplete()
                }
            })
            
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result == TextToSpeech.ERROR) {
                Log.e("AndroidTTS", "TTS speak 호출 실패")
                onError("TTS speak 호출 실패")
                onComplete()
            } else {
                Log.d("AndroidTTS", "TTS speak 호출 성공")
            }
            
        } catch (e: Exception) {
            Log.e("AndroidTTS", "TTS 재생 중 오류 발생", e)
            onError("TTS 재생 중 오류: ${e.message}")
            onComplete()
        }
    }
    
    fun stop() {
        Log.d("AndroidTTS", "TTS 중지")
        textToSpeech?.stop()
    }
    
    fun release() {
        Log.d("AndroidTTS", "TTS 해제")
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        selectedVoice = null
    }
    
    fun isAvailable(): Boolean {
        return isInitialized && textToSpeech != null
    }
} 