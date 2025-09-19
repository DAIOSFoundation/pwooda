package com.banya.neulpum.presentation.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.banya.neulpum.utils.PermissionHelper
import com.banya.neulpum.utils.rememberPermissionHelper
import com.banya.neulpum.utils.rememberPermissionLauncher
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraComponent(
    onImageCaptured: (Bitmap?) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionHelper = rememberPermissionHelper()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            permissionHelper.isPermissionGranted(PermissionHelper.CAMERA_PERMISSION)
        )
    }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 권한 요청 런처
    val permissionLauncher = rememberPermissionLauncher { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            // 권한이 거부되었을 때 설정 다이얼로그 표시
            showSettingsDialog = true
        }
    }
    
    if (hasCameraPermission) {
        CameraContent(
            onImageCaptured = onImageCaptured,
            onError = onError,
            onClose = onClose
        )
    } else {
        PermissionRequest(
            onRequestPermission = { 
                permissionLauncher.launch(PermissionHelper.CAMERA_PERMISSION)
            }
        )
    }
    
    // 권한 요청 다이얼로그 (권한이 거부되었을 때 설정으로 안내)
    if (showSettingsDialog) {
        CameraPermissionDialog(
            onConfirm = {
                showSettingsDialog = false
                permissionHelper.openAppSettings()
            },
            onDismiss = {
                showSettingsDialog = false
                onClose()
            }
        )
    }
}

@Composable
private fun CameraContent(
    onImageCaptured: (Bitmap?) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCameraReady by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageCapture: ImageCapture = remember { buildImageCapture() }
    val cameraSelector: CameraSelector = remember { 
        CameraSelector.Builder().requireLensFacing(lensFacing).build() 
    }
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // 뒤로가기 버튼으로 닫기
    BackHandler { onClose() }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 카메라 프리뷰
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                cameraProviderFuture.addListener({
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        provider.unbindAll()
                        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageCapture
                        )
                        // 약간의 지연을 두고 카메라 준비 완료 상태로 설정
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isCameraReady = true
                        }, 500)
                    } catch (e: Exception) {
                        onError("카메라를 시작할 수 없습니다: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            update = { previewView ->
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )
                    // 약간의 지연을 두고 카메라 준비 완료 상태로 설정
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isCameraReady = true
                    }, 500)
                } catch (e: Exception) {
                    onError("카메라를 시작할 수 없습니다: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 상단 컨트롤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 닫기 버튼
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // 전면/후면 카메라 전환
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.White.copy(alpha = 0.7f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cameraswitch,
                    contentDescription = "카메라 전환",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        // 하단 촬영 버튼 (셔터 스타일)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 외곽 링
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(6.dp)
            ) {
                // 내부 셔터 버튼
                Button(
                    onClick = {
                        if (isCameraReady && cameraProvider != null) {
                            takePhoto(
                                imageCapture = imageCapture,
                                outputDirectory = context.getOutputDirectory(),
                                executor = context.executor,
                                onImageCaptured = onImageCaptured,
                                onError = onError
                            )
                        }
                    },
                    enabled = isCameraReady && cameraProvider != null,
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCameraReady && cameraProvider != null) Color.White else Color.Gray
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) { }
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "카메라 권한이 필요합니다",
            color = Color.White,
            fontSize = 20.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "제품을 촬영하려면 카메라 권한을 허용해주세요",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10A37F)
            )
        ) {
            Text("권한 요청")
        }
    }
}

private fun buildImageCapture(): ImageCapture {
    return ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
}

private fun takePhoto(
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: ExecutorService,
    onImageCaptured: (Bitmap?) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val photoFile = File(
            outputDirectory,
            "IMG_${System.currentTimeMillis()}.jpg"
        )
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                        if (bitmap != null) {
                            val rotated = rotateAccordingToExif(photoFile, bitmap)
                            val resized = resizeImageWithAspectRatio(rotated, 512)
                            if (rotated != bitmap) bitmap.recycle()
                            if (resized != rotated) rotated.recycle()
                            onImageCaptured(resized)
                        } else {
                            onError("이미지를 로드할 수 없습니다")
                        }
                    } catch (e: Exception) {
                        onError("이미지 처리 중 오류: ${e.message}")
                    }
                }
                
                override fun onError(exc: ImageCaptureException) {
                    onError("사진 촬영에 실패했습니다: ${exc.message}")
                }
            }
        )
    } catch (e: Exception) {
        onError("카메라 촬영 중 오류가 발생했습니다: ${e.message}")
    }
}

private fun rotateAccordingToExif(file: File, bitmap: Bitmap): Bitmap {
    return try {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap
    } catch (e: Exception) {
        bitmap
    }
}

private fun resizeImageWithAspectRatio(bitmap: Bitmap, maxSize: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxSize && height <= maxSize) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    val (newW, newH) = if (ratio > 1f) {
        maxSize to (maxSize / ratio).toInt()
    } else {
        (maxSize * ratio).toInt() to maxSize
    }
    return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
}

private fun Context.getOutputDirectory(): File {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
        File(it, "neulpum").apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
}

private val Context.executor: ExecutorService
    get() = Executors.newSingleThreadExecutor() 