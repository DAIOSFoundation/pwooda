package com.banya.pwooda.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetectionService(private val context: Context) {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()
    
    private val faceDetectionChannel = Channel<Boolean>(Channel.CONFLATED)
    private var lastFaceDetectionTime = 0L
    private var onFaceDetectedCallback: (() -> Unit)? = null
    
    // 얼굴 감지 옵션 설정
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()
    
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)
    
    fun startFaceDetection(lifecycleOwner: LifecycleOwner, onFaceDetected: () -> Unit) {
        Log.d("FaceDetection", "얼굴 감지 시작")
        
        // 콜백 저장
        onFaceDetectedCallback = onFaceDetected
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis!!
                )
                Log.d("FaceDetection", "전면 카메라 바인딩 완료")
            } catch (e: Exception) {
                Log.e("FaceDetection", "카메라 바인딩 실패", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    val currentTime = System.currentTimeMillis()
                    
                    if (faces.isNotEmpty()) {
                        if (!_isFaceDetected.value) {
                            Log.d("FaceDetection", "얼굴 감지됨: ${faces.size}개")
                            _isFaceDetected.value = true
                        }
                        
                        // 20초마다 환영 메시지 출력
                        if (currentTime - lastFaceDetectionTime > 20000) {
                            Log.d("FaceDetection", "20초 경과, 환영 메시지 출력")
                            lastFaceDetectionTime = currentTime
                            onFaceDetectedCallback?.invoke()
                        }
                    } else if (faces.isEmpty() && _isFaceDetected.value) {
                        Log.d("FaceDetection", "얼굴이 사라짐")
                        _isFaceDetected.value = false
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceDetection", "얼굴 감지 실패", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    fun stopFaceDetection() {
        Log.d("FaceDetection", "얼굴 감지 중지")
        try {
            cameraProvider?.unbindAll()
            imageAnalysis?.clearAnalyzer()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e("FaceDetection", "얼굴 감지 중지 중 오류", e)
        }
    }
    
    fun resetFaceDetection() {
        _isFaceDetected.value = false
    }
} 