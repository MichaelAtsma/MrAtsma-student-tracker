package com.example.mratsmastudenttracker.ui

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    sessionId: Long,
    viewModel: StudentViewModel,
    collaborationViewModel: CollaborationViewModel,
    onNavigateBack: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var lastScannedId by remember { mutableStateOf<String?>(null) }
    var overlayColor by remember { mutableStateOf<Color?>(null) }
    
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (collaborationViewModel.isAssistant) "Assistant Scanner" else "Scan Student QR") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val scanner = BarcodeScanning.getClient(
                                BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .build()
                            )

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                processImageProxy(scanner, imageProxy) { rawValue ->
                                    if (rawValue != lastScannedId) {
                                        lastScannedId = rawValue
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()

                                            if (collaborationViewModel.isAssistant) {
                                                val (success, isTargeted, message) = collaborationViewModel.scanStudentAsAssistant(rawValue)
                                                if (success) {
                                                    overlayColor = if (isTargeted) Color.Green else Color.Yellow
                                                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 150)
                                                } else {
                                                    overlayColor = Color.Red
                                                    toneGenerator.startTone(ToneGenerator.TONE_SUP_ERROR, 250)
                                                }
                                                snackbarHostState.showSnackbar(message)
                                            } else {
                                                val student = viewModel.getStudentById(rawValue).firstOrNull()
                                                if (student != null) {
                                                    val isTargeted = viewModel.isStudentTargetedForSession(sessionId, rawValue)
                                                    viewModel.markAttendance(sessionId, rawValue)
                                                    overlayColor = if (isTargeted) Color.Green else Color.Yellow
                                                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 150)
                                                    val prefix = if (isTargeted) "Scanned student" else "Scanned student (Not targeted)"
                                                    snackbarHostState.showSnackbar("$prefix: ${student.firstName} ${student.lastName}")
                                                } else {
                                                    overlayColor = Color.Red
                                                    toneGenerator.startTone(ToneGenerator.TONE_SUP_ERROR, 250)
                                                    snackbarHostState.showSnackbar("Student not found: $rawValue")
                                                }
                                            }
                                            
                                            delay(250)
                                            overlayColor = null
                                            delay(1750)
                                            lastScannedId = null
                                        }
                                    }
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ScannerOverlay(overlayColor)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Camera permission required")
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onSuccess: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { onSuccess(it) }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun ScannerOverlay(overlayColor: Color? = null) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 4.dp.toPx()
        val cornerRadius = 16.dp.toPx()
        val rectSize = size.width * 0.7f
        val left = (size.width - rectSize) / 2
        val top = (size.height - rectSize) / 2

        drawRect(
            color = (overlayColor ?: Color.Black).copy(alpha = 0.5f)
        )

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(rectSize, rectSize),
            cornerRadius = CornerRadius(cornerRadius),
            blendMode = BlendMode.Clear
        )

        drawRoundRect(
            color = overlayColor ?: Color.White,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(rectSize, rectSize),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = strokeWidth)
        )
    }
}
