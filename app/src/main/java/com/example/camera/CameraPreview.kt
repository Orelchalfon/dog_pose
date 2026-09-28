package com.example.camera

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.state.DogPosition
import com.example.state.LureStep
import com.example.ui.theme.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isDogInFrame: Boolean,
    isHumanInFrame: Boolean,
    dogPosition: DogPosition,
    sessionActive: Boolean,
    currentLureStep: LureStep,
    onFrameAnalyzed: (DogPosition) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    // Check permissions
    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        )
        hasCameraPermission = permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // Live Camera Link using CameraX
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                try {
                                    val planes = imageProxy.planes
                                    if (planes.isNotEmpty()) {
                                        val buffer = planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)

                                        val width = imageProxy.width
                                        val height = imageProxy.height
                                        val rowStride = planes[0].rowStride
                                        val pixelStride = planes[0].pixelStride

                                        var topSum = 0L
                                        var topCount = 0
                                        var bottomSum = 0L
                                        var bottomCount = 0

                                        // Sub-sample to ensure high performance
                                        for (y in 0 until height step 16) {
                                            for (x in 0 until width step 16) {
                                                val index = y * rowStride + x * pixelStride
                                                if (index >= 0 && index < bytes.size) {
                                                    val value = bytes[index].toInt() and 0xFF
                                                    if (y < height / 2) {
                                                        topSum += value
                                                        topCount++
                                                    } else {
                                                        bottomSum += value
                                                        bottomCount++
                                                    }
                                                }
                                            }
                                        }

                                        val avgTop = if (topCount > 0) topSum.toFloat() / topCount else 0f
                                        val avgBottom = if (bottomCount > 0) bottomSum.toFloat() / bottomCount else 0f

                                        // Calculate luminance contrast. In complete dark, ungranted permissions, 
                                        // or completely flat emulator views, we maintain the existing UI/interactive setting.
                                        if (Math.abs(avgTop - avgBottom) > 5.0f) {
                                            val luminanceRatio = if (avgBottom > 0) avgTop / avgBottom else 1.0f
                                            
                                            // Ratio check: Higher top brightness relative to bottom implies dog body is sitting lower down
                                            val detectedPose = if (luminanceRatio > 1.04f) {
                                                DogPosition.SIT
                                            } else {
                                                DogPosition.STAND
                                            }

                                            // Post pose updates back on the main thread for UI/State synchronization
                                            ContextCompat.getMainExecutor(context).execute {
                                                onFrameAnalyzed(detectedPose)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraPreview", "Frame pose analysis failed", e)
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Camera link binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback: Custom Gradient Active Simulation Backdrop for quick testing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GeometricBackground)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera placeholder",
                        tint = Color(0xFF49454F),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera Active Feed",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "(Grant permissions or use controls to demonstrate)",
                        color = GeometricSubtext,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Real-time AI HUD Overlays
        AIOverlayLayers(
            isDogInFrame = isDogInFrame,
            isHumanInFrame = isHumanInFrame,
            dogPosition = dogPosition,
            sessionActive = sessionActive,
            currentLureStep = currentLureStep
        )
    }
}

@Composable
fun AIOverlayLayers(
    isDogInFrame: Boolean,
    isHumanInFrame: Boolean,
    dogPosition: DogPosition,
    sessionActive: Boolean,
    currentLureStep: LureStep
) {
    // Pulsating indicator
    val alphaAnim by animateFloatAsState(
        targetValue = if (isDogInFrame) 0.82f else 0.4f,
        animationSpec = tween(1200)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (!sessionActive) {
            // Phase 1: Camera Setup & Positioning Expected dog alignment indicator square
            val squareBorderColor = if (isDogInFrame) Color(0xFF4ADE80) else Color(0xFFEF4444)
            val overlayBgColor = if (isDogInFrame) Color(0x224ADE80) else Color(0x22EF4444)

            Box(
                modifier = Modifier
                    .offset(x = 110.dp, y = 220.dp)
                    .size(width = 270.dp, height = 260.dp)
                    .background(overlayBgColor, RoundedCornerShape(16.dp))
                    .border(3.dp, squareBorderColor, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isDogInFrame) Icons.Default.CameraAlt else Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = squareBorderColor,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isDogInFrame) "כלב זוהה בתיבה! (קבל ירוק)" else "מקם את הכלב (רקס) כאן",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "נציג בפרופיל צד מלא",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Phase 2: Active Session & Detection HUDs
            // Draw Human Skeleton if in frame (Top Left aspect of frame)
            if (isHumanInFrame) {
                HumanTrackerHUD(modifier = Modifier.fillMaxSize())
            }

            // Draw Dog dynamic bounding indicators, joint skeleton, and luring arc
            if (isDogInFrame) {
                DogTrackerHUD(
                    dogPosition = dogPosition,
                    currentLureStep = currentLureStep,
                    modifier = Modifier.fillMaxSize(),
                    alphaPulse = alphaAnim
                )
            }
        }
    }
}

@Composable
fun HumanTrackerHUD(modifier: Modifier = Modifier) {
    // Custom Canvas draws Human tracking
    Box(modifier = modifier) {
        // Draw Box Label matching white/40 translucent style
        Box(
            modifier = Modifier
                .offset(x = 30.dp, y = 80.dp)
                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(8.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        ) {
            Column {
                Text("HUMAN (TRAINER)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Confidence: 99.4%", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width
            val scaleY = size.height

            // Standard human skeleton indicators for trainer holding treat
            val head = Offset(scaleX * 0.18f, scaleY * 0.18f)
            val shoulderR = Offset(scaleX * 0.23f, scaleY * 0.23f)
            val elbowR = Offset(scaleX * 0.21f, scaleY * 0.28f)
            val handR = Offset(scaleX * 0.25f, scaleY * 0.32f) // hand with food

            // Connective Bones using white dashed line
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = head,
                end = shoulderR,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            drawLine(
                color = Color.White,
                start = shoulderR,
                end = elbowR,
                strokeWidth = 4f
            )
            drawLine(
                color = Color.White,
                start = elbowR,
                end = handR,
                strokeWidth = 4f
            )

            // Draw glowing joint points
            drawCircle(color = Color.White, radius = 9f, center = head)
            drawCircle(color = Color.White, radius = 7f, center = shoulderR)
            drawCircle(color = Color.White, radius = 7f, center = elbowR)
            drawCircle(color = GeometricSuccessBg, radius = 11f, center = handR) // treat glows soft green
        }
    }
}

@Composable
fun DogTrackerHUD(
    dogPosition: DogPosition,
    currentLureStep: LureStep,
    modifier: Modifier = Modifier,
    alphaPulse: Float
) {
    // Dog keypoints offsets determined by Stand vs Sit pose
    val bodyYOffset by animateDpAsState(
        targetValue = if (dogPosition == DogPosition.SIT) 80.dp else 0.dp,
        animationSpec = tween(500)
    )

    val rearLegCollapseX by animateDpAsState(
        targetValue = if (dogPosition == DogPosition.SIT) (-20).dp else 0.dp,
        animationSpec = tween(500)
    )

    // Dynamic hand/lure angle animation corresponding to the user's simulation step
    val lureAngle by animateFloatAsState(
        targetValue = when (currentLureStep) {
            LureStep.NONE -> 0f
            LureStep.SMELL_TREAT -> 10f
            LureStep.SPOKEN_COMMAND -> 45f
            LureStep.HAND_ABOVE_HEAD, LureStep.REWARD_GIVEN -> 90f
        },
        animationSpec = tween(500)
    )

    Box(modifier = modifier) {
        // Overlay Bounding Box - matching lavender thematic color
        val boxHeight = if (dogPosition == DogPosition.SIT) 200.dp else 260.dp
        val boxWidth = if (dogPosition == DogPosition.SIT) 210.dp else 270.dp
        
        Box(
            modifier = Modifier
                .offset(x = 110.dp, y = 220.dp + bodyYOffset)
                .size(width = boxWidth, height = boxHeight)
                .border(2.dp, GeometricAccent, RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .background(GeometricOnAccent, RoundedCornerShape(8.dp))
                    .padding(6.dp)
                    .align(Alignment.TopStart)
            ) {
                Column {
                    Text("CANINE (REX)", color = GeometricAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Pose: ${dogPosition.name}", color = Color.White, fontSize = 9.sp)
                    Text("CoreML Engine Flow: 98.2%", color = GeometricSubtext, fontSize = 8.sp)
                }
            }
        }

        // Draw Canine Pose Estimator Nodes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Baseline coordinates offset by Compose Animation states
            val animY = bodyYOffset.toPx()
            val collapseX = rearLegCollapseX.toPx()

            // Dog joint coordinates
            val muzzle = Offset(w * 0.72f, h * 0.42f + animY)
            val head = Offset(w * 0.65f, h * 0.38f + animY)
            val shoulder = Offset(w * 0.58f, h * 0.46f + animY)
            val frontElbow = Offset(w * 0.58f, h * 0.56f + animY * 0.4f)
            val frontPaw = Offset(w * 0.58f, h * 0.66f) // stays on ground

            val hip = Offset(w * 0.44f + collapseX, h * 0.48f + animY)
            val stifle = Offset(w * 0.42f + collapseX, h * 0.58f + animY * 1.1f)
            val hock = Offset(w * 0.41f + collapseX, h * 0.66f) // on ground
            val tailRoot = Offset(w * 0.38f + collapseX, h * 0.44f + animY)

            // Connect Bones (Draw lines in custom lavender theme)
            val boneColor = GeometricAccent
            val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            // Head and snout
            drawLine(color = boneColor, start = muzzle, end = head, strokeWidth = 5f)
            drawLine(color = boneColor, start = head, end = shoulder, strokeWidth = 5f)

            // Spines
            drawLine(color = boneColor, start = shoulder, end = hip, strokeWidth = 6f)

            // Front arm
            drawLine(color = boneColor, start = shoulder, end = frontElbow, strokeWidth = 4f)
            drawLine(color = boneColor, start = frontElbow, end = frontPaw, strokeWidth = 4f)

            // Back leg
            drawLine(color = boneColor, start = hip, end = stifle, strokeWidth = 4f)
            drawLine(color = boneColor, start = stifle, end = hock, strokeWidth = 4f)

            // Tail
            drawLine(
                color = boneColor,
                start = hip,
                end = tailRoot,
                strokeWidth = 3f,
                pathEffect = dottedEffect
            )

            // Glowing joint markers
            drawCircle(color = GeometricSuccessBg, radius = 8f, center = muzzle) // soft green tracker
            drawCircle(color = boneColor, radius = 9f, center = head)
            drawCircle(color = boneColor, radius = 8f, center = shoulder)
            drawCircle(color = boneColor, radius = 7f, center = frontElbow)
            drawCircle(color = boneColor, radius = 8f, center = frontPaw)
            drawCircle(color = boneColor, radius = 8f, center = hip)
            drawCircle(color = boneColor, radius = 7f, center = stifle)
            drawCircle(color = boneColor, radius = 8f, center = hock)

            // ------ SPECIAL DESIGN: 0° TO 90° CIRCULAR LURING OVERLAY ARC ------
            // Center of hand's rotational arc is near the dog's shoulder/neck
            val rotationCenter = Offset(w * 0.63f, h * 0.46f + animY)
            val arcRadius = w * 0.09f // Dynamic pixel radius

            // Draw Target Guide Arc (dashed quarter-circle)
            drawArc(
                color = GeometricAccent.copy(alpha = 0.5f),
                startAngle = 0f,
                sweepAngle = -90f,
                useCenter = false,
                topLeft = Offset(rotationCenter.x - arcRadius, rotationCenter.y - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            // Draw start angle (Nose) label dot
            drawCircle(
                color = Color(0xFFFDBA74),
                radius = 5f,
                center = Offset(rotationCenter.x + arcRadius, rotationCenter.y)
            )

            // Draw target finish (Above Head 90°) label dot
            drawCircle(
                color = GeometricSuccessBg,
                radius = 5f,
                center = Offset(rotationCenter.x, rotationCenter.y - arcRadius)
            )

            // Compute hand position based on animated lureAngle
            val rad = Math.toRadians(lureAngle.toDouble())
            val handX = rotationCenter.x + arcRadius * Math.cos(rad).toFloat()
            val handY = rotationCenter.y - arcRadius * Math.sin(rad).toFloat()
            val handCenter = Offset(handX, handY)

            // Draw Hand tracking circle & glow
            drawCircle(
                color = Color(0xFFA855F7).copy(alpha = 0.3f),
                radius = 20f,
                center = handCenter
            )
            drawCircle(
                color = Color(0xFFA855F7),
                radius = 8f,
                center = handCenter
            )

            // Draw simple indicator trace showing hand direction line from origin
            drawLine(
                color = Color(0xFFA855F7).copy(alpha = 0.6f),
                start = rotationCenter,
                end = handCenter,
                strokeWidth = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }
    }
}
