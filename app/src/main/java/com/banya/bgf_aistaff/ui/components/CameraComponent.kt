package com.banya.bgf_aistaff.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaPlayer
import android.media.ToneGenerator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraComponent(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 셔터 소리를 위한 MediaPlayer
    val shutterSound = remember { MediaPlayer() }
    
    // 컴포넌트가 사라질 때 MediaPlayer 해제
    DisposableEffect(Unit) {
        onDispose {
            shutterSound.release()
        }
    }
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    
    // 카메라 권한 확인
    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context, 
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    
    LaunchedEffect(Unit) {
        if (hasCameraPermission) {
            try {
                cameraProvider = context.getCameraProvider()
                isCameraReady = true
            } catch (e: Exception) {
                onError("카메라 초기화 실패: ${e.message}")
            }
        } else {
            onError("카메라 권한이 필요합니다")
        }
    }
    
    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("카메라 권한이 필요합니다", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    
    if (!isCameraReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                
                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    onError("카메라를 시작할 수 없습니다: ${e.message}")
                }
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 카메라 컨트롤 버튼들
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (imageCapture != null) {
                        // 셔터 소리 재생
                        playShutterSound(context, shutterSound)
                        
                        takePhoto(
                            imageCapture = imageCapture!!,
                            outputDirectory = context.getOutputDirectory(),
                            executor = ContextCompat.getMainExecutor(context),
                            onImageCaptured = onImageCaptured,
                            onError = onError
                        )
                    } else {
                        onError("카메라가 준비되지 않았습니다")
                    }
                },
                modifier = Modifier.size(120.dp, 60.dp)
            ) {
                Text("사진 촬영")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "카메라가 준비되었습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    outputDirectory: java.io.File,
    executor: Executor,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val photoFile = java.io.File(
        outputDirectory,
        "photo_${System.currentTimeMillis()}.jpg"
    )
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    // Bitmap으로 변환
                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap != null) {
                        // 이미지 회전 처리
                        val rotatedBitmap = rotateImage(bitmap, 90f)
                        // 원본 비트맵 해제
                        bitmap.recycle()
                        
                        // 이미지를 비율 유지하면서 최대 512px로 리사이즈
                        val resizedBitmap = resizeImageWithAspectRatio(rotatedBitmap, 512)
                        // 회전된 비트맵 해제
                        rotatedBitmap.recycle()
                        
                        android.util.Log.d("CameraComponent", "이미지 처리 완료 - 원본: ${bitmap.width}x${bitmap.height}, 최종: ${resizedBitmap.width}x${resizedBitmap.height}")
                        onImageCaptured(resizedBitmap)
                    } else {
                        onError("이미지를 로드할 수 없습니다")
                    }
                } catch (e: Exception) {
                    onError("이미지 처리 실패: ${e.message}")
                } finally {
                    // 임시 파일 삭제
                    photoFile.delete()
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                onError("사진 촬영 실패: ${exception.message}")
                photoFile.delete()
            }
        }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }
}

private fun Context.getOutputDirectory(): java.io.File {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
        java.io.File(it, "BGF_AIStaff").apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
}

private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply {
        postRotate(degrees)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun resizeImageWithAspectRatio(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    
    // 비율 계산
    val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
    
    val newWidth: Int
    val newHeight: Int
    
    if (originalWidth > originalHeight) {
        // 가로가 더 긴 경우
        if (originalWidth > maxDimension) {
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            // 이미 충분히 작은 경우 원본 크기 유지
            newWidth = originalWidth
            newHeight = originalHeight
        }
    } else {
        // 세로가 더 긴 경우
        if (originalHeight > maxDimension) {
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        } else {
            // 이미 충분히 작은 경우 원본 크기 유지
            newWidth = originalWidth
            newHeight = originalHeight
        }
    }
    
    // 비율을 유지하면서 리사이즈 (필요한 경우에만)
    return if (newWidth != originalWidth || newHeight != originalHeight) {
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else {
        // 크기가 같으면 원본 반환 (새로운 비트맵 생성하지 않음)
        bitmap
    }
}

private fun playShutterSound(context: Context, mediaPlayer: MediaPlayer) {
    try {
        // raw 폴더의 shutter_sound.mp3 파일 재생
        val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/raw/shutter_sound")
        mediaPlayer.apply {
            reset()
            setDataSource(context, soundUri)
            prepare()
            start()
        }
    } catch (e: Exception) {
        // 셔터 소리 재생 실패 시 무시 (앱 동작에 영향 없음)
        android.util.Log.w("CameraComponent", "셔터 소리 재생 실패: ${e.message}")
    }
} 