package com.banya.pwooda.service

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class FalAIService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // ComfyUI는 시간이 더 오래 걸릴 수 있음
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ComfyUI API 엔드포인트
    private val COMFYUI_BASE_URL = "http://192.168.219.122:8000"

    // 프롬프트 최적화 (이미 Gemini에서 변환된 영문 프롬프트 사용)
    private fun optimizePrompt(englishPrompt: String): String {
        // 프롬프트가 이미 최적화되어 있으므로 그대로 반환
        return englishPrompt
    }

    suspend fun generateImage(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("FalAIService", "ComfyUI 이미지 생성 시작 - 원본 프롬프트: $prompt")
            
            // 프롬프트 최적화 (이미 Gemini에서 변환된 영문 프롬프트)
            val optimizedPrompt = optimizePrompt(prompt)
            Log.d("FalAIService", "최적화된 프롬프트: $optimizedPrompt")

            // 1. 워크플로우 생성 및 실행
            val promptId = createAndExecuteWorkflow(optimizedPrompt)
            if (promptId == null) {
                Log.e("FalAIService", "워크플로우 실행 실패")
                return@withContext null
            }

            // 2. 이미지 생성 완료 대기
            val imageData = waitForImageGeneration(promptId)
            if (imageData == null) {
                Log.e("FalAIService", "이미지 생성 실패")
                return@withContext null
            }

            Log.d("FalAIService", "ComfyUI 이미지 생성 완료")
            return@withContext imageData

        } catch (e: Exception) {
            Log.e("FalAIService", "ComfyUI 이미지 생성 중 오류 발생", e)
            return@withContext null
        }
    }

    // ComfyUI 워크플로우 생성 및 실행
    private suspend fun createAndExecuteWorkflow(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            // 기본 Stable Diffusion 워크플로우 생성
            val workflow = createBasicWorkflow(prompt)
            
            val requestBody = JSONObject().apply {
                put("prompt", workflow)
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$COMFYUI_BASE_URL/prompt")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            Log.d("FalAIService", "ComfyUI 워크플로우 실행 요청 전송")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("FalAIService", "워크플로우 실행 실패: ${response.code}")
                    return@withContext null
                }

                val responseBody = response.body?.string()
                if (responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val promptId = jsonResponse.optString("prompt_id")
                        if (promptId.isNotEmpty()) {
                            Log.d("FalAIService", "워크플로우 실행 성공, prompt_id: $promptId")
                            return@withContext promptId
                        }
                    } catch (e: Exception) {
                        Log.e("FalAIService", "워크플로우 응답 파싱 오류", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FalAIService", "워크플로우 실행 중 오류", e)
        }
        return@withContext null
    }

    // SDXL 모델을 사용하는 간단한 워크플로우 생성
    private fun createBasicWorkflow(prompt: String): JSONObject {
        val workflow = JSONObject()
        
        // CheckpointLoaderSimple 노드 (SDXL 모델)
        val checkpointLoader = JSONObject().apply {
            put("class_type", "CheckpointLoaderSimple")
            put("inputs", JSONObject().apply {
                put("ckpt_name", "sd_xl_base_1.0.safetensors")
            })
        }
        
        // CLIPTextEncode (Positive) 노드
        val positivePrompt = JSONObject().apply {
            put("class_type", "CLIPTextEncode")
            put("inputs", JSONObject().apply {
                put("text", prompt)
                put("clip", JSONArray().apply {
                    put("1")
                    put(1)
                })
            })
        }
        
        // CLIPTextEncode (Negative) 노드
        val negativePrompt = JSONObject().apply {
            put("class_type", "CLIPTextEncode")
            put("inputs", JSONObject().apply {
                put("text", "blurry, low quality, distorted, deformed, ugly, bad anatomy, watermark, signature, text, extra limbs, poorly drawn hands, poorly drawn feet, poorly drawn face, mutation, mutated, extra limbs, extra arms, extra legs, disfigured, out of frame, duplicate, cropped, worst quality, low quality, normal quality, jpeg artifacts, signature, watermark, username, blurry")
                put("clip", JSONArray().apply {
                    put("1")
                    put(1)
                })
            })
        }
        
        // EmptyLatentImage 노드
        val emptyLatent = JSONObject().apply {
            put("class_type", "EmptyLatentImage")
            put("inputs", JSONObject().apply {
                put("width", 512)
                put("height", 512)
                put("batch_size", 1)
            })
        }
        
        // KSampler 노드
        val kSampler = JSONObject().apply {
            put("class_type", "KSampler")
            put("inputs", JSONObject().apply {
                put("seed", (Math.random() * 1000000).toInt())
                put("steps", 20) // 지브리 스타일을 위해 스텝 수 증가
                put("cfg", 8.0) // 지브리 스타일을 위해 CFG 값 증가
                put("sampler_name", "dpmpp_2m") // 더 나은 품질을 위한 샘플러
                put("scheduler", "karras") // 지브리 스타일에 적합한 스케줄러
                put("denoise", 1.0)
                put("model", JSONArray().apply {
                    put("1")
                    put(0)
                })
                put("positive", JSONArray().apply {
                    put("2")
                    put(0)
                })
                put("negative", JSONArray().apply {
                    put("3")
                    put(0)
                })
                put("latent_image", JSONArray().apply {
                    put("4")
                    put(0)
                })
            })
        }
        
        // VAE Decode 노드
        val vaeDecode = JSONObject().apply {
            put("class_type", "VAEDecode")
            put("inputs", JSONObject().apply {
                put("samples", JSONArray().apply {
                    put("5")
                    put(0)
                })
                put("vae", JSONArray().apply {
                    put("1")
                    put(2)
                })
            })
        }
        
        // SaveImage 노드
        val saveImage = JSONObject().apply {
            put("class_type", "SaveImage")
            put("inputs", JSONObject().apply {
                put("images", JSONArray().apply {
                    put("6")
                    put(0)
                })
                put("filename_prefix", "SDXLImage")
            })
        }
        
        // 워크플로우에 노드 추가
        workflow.put("1", checkpointLoader)
        workflow.put("2", positivePrompt)
        workflow.put("3", negativePrompt)
        workflow.put("4", emptyLatent)
        workflow.put("5", kSampler)
        workflow.put("6", vaeDecode)
        workflow.put("7", saveImage)
        
        return workflow
    }

    // 이미지 생성 완료 대기
    private suspend fun waitForImageGeneration(promptId: String): String? = withContext(Dispatchers.IO) {
        try {
            var attempts = 0
            val maxAttempts = 120 // 최대 120초 대기 (1초마다 체크)
            
            while (attempts < maxAttempts) {
                kotlinx.coroutines.delay(1000) // 1초 대기
                attempts++
                
                Log.d("FalAIService", "이미지 생성 상태 확인 중... (시도 $attempts)")
                
                val request = Request.Builder()
                    .url("$COMFYUI_BASE_URL/history")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                Log.d("FalAIService", "히스토리 응답: $responseBody")
                                
                                val promptData = jsonResponse.optJSONObject(promptId)
                                Log.d("FalAIService", "프롬프트 데이터 찾음: ${promptData != null}")
                                
                                if (promptData != null) {
                                    val outputs = promptData.optJSONObject("outputs")
                                    Log.d("FalAIService", "출력 데이터 찾음: ${outputs != null}")
                                    
                                    if (outputs != null) {
                                        // SaveImage 노드의 출력 확인
                                        val saveImageOutput = outputs.optJSONObject("7")
                                        Log.d("FalAIService", "SaveImage 출력 찾음: ${saveImageOutput != null}")
                                        
                                        if (saveImageOutput != null) {
                                            val images = saveImageOutput.optJSONArray("images")
                                            Log.d("FalAIService", "이미지 배열 찾음: ${images != null}, 길이: ${images?.length()}")
                                            
                                            if (images != null && images.length() > 0) {
                                                val imageInfo = images.getJSONObject(0)
                                                val filename = imageInfo.optString("filename")
                                                val subfolder = imageInfo.optString("subfolder", "")
                                                
                                                Log.d("FalAIService", "파일명: $filename, 서브폴더: $subfolder")
                                                
                                                // 이미지 URL 생성
                                                val imageUrl = "$COMFYUI_BASE_URL/view?filename=$filename&subfolder=$subfolder&type=output"
                                                Log.d("FalAIService", "이미지 생성 완료: $imageUrl")
                                                return@withContext imageUrl
                                            } else {
                                                Log.e("FalAIService", "이미지 배열이 비어있음")
                                            }
                                        } else {
                                            Log.e("FalAIService", "SaveImage 출력을 찾을 수 없음")
                                        }
                                    } else {
                                        Log.e("FalAIService", "출력 데이터를 찾을 수 없음")
                                    }
                                } else {
                                    Log.e("FalAIService", "프롬프트 데이터를 찾을 수 없음")
                                }
                            } catch (e: Exception) {
                                Log.e("FalAIService", "상태 확인 응답 파싱 오류", e)
                            }
                        }
                    }
                }
            }
            
            Log.e("FalAIService", "이미지 생성 타임아웃")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e("FalAIService", "이미지 생성 대기 중 오류", e)
            return@withContext null
        }
    }

    // Hugging Face API는 상태 확인이 필요 없으므로 더미 함수
    suspend fun checkImageStatus(requestId: String): String? = withContext(Dispatchers.IO) {
        // Hugging Face API는 이미지 URL을 직접 반환하므로 이 함수는 사용되지 않음
        return@withContext null
    }

    suspend fun downloadImage(imageData: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Log.d("FalAIService", "이미지 디코딩 시작")
            
            if (imageData.startsWith("data:image/jpeg;base64,")) {
                // Base64 디코딩
                val base64Data = imageData.substring("data:image/jpeg;base64,".length)
                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    Log.d("FalAIService", "이미지 디코딩 완료: ${bitmap.width}x${bitmap.height}")
                    return@withContext bitmap
                } else {
                    Log.e("FalAIService", "이미지 디코딩 실패")
                    return@withContext null
                }
            } else {
                // URL인 경우 기존 방식 사용
                val request = Request.Builder()
                    .url(imageData)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("FalAIService", "이미지 다운로드 실패: ${response.code}")
                        return@withContext null
                    }

                    val inputStream = response.body?.byteStream()
                    if (inputStream != null) {
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        Log.d("FalAIService", "이미지 다운로드 완료: ${bitmap.width}x${bitmap.height}")
                        return@withContext bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FalAIService", "이미지 처리 중 오류 발생", e)
        }
        return@withContext null
    }
} 