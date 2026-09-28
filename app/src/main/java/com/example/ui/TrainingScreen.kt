package com.example.ui

import android.widget.Space
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraPreview
import com.example.speech.SpeechManager
import com.example.state.DogPosition
import com.example.state.LureStep
import com.example.state.TrainingViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dogName by viewModel.dogName.collectAsState()
    val trainerName by viewModel.trainerName.collectAsState()
    val targetSits by viewModel.targetSits.collectAsState()
    val sessionActive by viewModel.sessionActive.collectAsState()

    val isDogInFrame by viewModel.isDogInFrame.collectAsState()
    val isHumanInFrame by viewModel.isHumanInFrame.collectAsState()
    val dogPosition by viewModel.dogPosition.collectAsState()
    val sitCommandCount by viewModel.sitCommandCountInRep.collectAsState()
    val currentLureStep by viewModel.currentLureStep.collectAsState()
    val activeNotification by viewModel.activeNotification.collectAsState()
    val repetitionHistory by viewModel.repetitionHistory.collectAsState()

    var showControlPanel by remember { mutableStateOf(true) }
    var micPermissionGranted by remember { mutableStateOf(false) }

    // Speech manager integration
    var speechManager by remember { mutableStateOf<SpeechManager?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        if (granted) {
            speechManager?.start()
        }
    }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val hasMic = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)
        if (hasMic) {
            val check = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            )
            if (check == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                micPermissionGranted = true
            } else {
                micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Initialize & clean speech recognition loops
    DisposableEffect(micPermissionGranted) {
        if (micPermissionGranted) {
            speechManager = SpeechManager(context) {
                viewModel.onSitCommandSpoken()
            }.apply {
                start()
            }
        }
        onDispose {
            speechManager?.stop()
            speechManager = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "אימון פעיל: $dogName",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endSession()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeometricBackground.copy(alpha = 0.5f) // Geometric translucent overlay
                )
            )
        },
        containerColor = GeometricBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Viewfinder viewport & vector joint layout
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                isDogInFrame = isDogInFrame,
                isHumanInFrame = isHumanInFrame,
                dogPosition = dogPosition,
                sessionActive = sessionActive,
                currentLureStep = currentLureStep,
                onFrameAnalyzed = { detectedPose ->
                    viewModel.onDogPoseChanged(detectedPose)
                }
            )

            // Top Status Overlay (Session metrics, count tracking)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Repetition Status Circle Indicators
                    Box(
                        modifier = Modifier
                            .background(GeometricCardBg.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = GeometricAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${repetitionHistory.size} / $targetSits הושבות",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Mic Keyword Indicator (Listening for sit)
                    Box(
                        modifier = Modifier
                            .background(GeometricCardBg.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (micPermissionGranted) Color(0xFF22C55E) else Color(
                                            0xFFEF4444
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (micPermissionGranted) "מיקרופון פעיל: \"שב\"" else "ללא מיקרופון",
                                color = GeometricSubtext,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Hebrew Notification Banner Overlays (as specified)
                AnimatedVisibility(
                    visible = activeNotification != null,
                    enter = slideInVertically(initialOffsetY = { -80 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -80 }) + fadeOut(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    activeNotification?.let { alert ->
                        val quad = when (alert.ruleId) {
                            1 -> Quadruple(Color(0xFFFCA5A5), Icons.Default.Warning, Color(0xFFEF4444), Color(alert.backgroundColorHex)) // Repeated command - red
                            2 -> Quadruple(Color(0xFFFDA4AF), Icons.Default.ErrorOutline, Color(0xFFE11D48), Color(alert.backgroundColorHex)) // Incorrect lure - crimson
                            3 -> Quadruple(Color(0xFFFDBA74), Icons.Default.AccessTime, Color(0xFFEA580C), Color(alert.backgroundColorHex)) // Delayed reward - orange
                            4 -> Quadruple(GeometricSuccessBorder, Icons.Default.Celebration, Color(0xFF1A1C18), GeometricSuccessBg) // Correct Sit - Geometric alignment
                            else -> Quadruple(Color(0xFFCBD5E1), Icons.Default.Info, Color(0xFF111827), Color(alert.backgroundColorHex))
                        }
                        val bannerBorder = quad.first as Color
                        val bannerIcon = quad.second
                        val accentColor = quad.third as Color
                        val bgStyle = quad.fourth as Color

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = bgStyle
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, bannerBorder, RoundedCornerShape(20.dp))
                                .clickable { viewModel.dismissNotification() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = when (alert.ruleId) {
                                            1 -> "התראת פקודה מרובה"
                                            2 -> "טעות באופן האילוף"
                                            3 -> "עיכוב במתן גמול"
                                            4 -> "הפעולה בוצעה בהצלחה"
                                            else -> "הדרכת אילוף"
                                        },
                                        color = if (alert.ruleId == 4) Color(0xFF386A20) else accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = alert.text,
                                        color = if (alert.ruleId == 4) Color(0xFF1A1C18) else Color(0xFF111827),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (alert.ruleId == 4) Color(0xFF386A20).copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = bannerIcon,
                                        contentDescription = null,
                                        tint = if (alert.ruleId == 4) Color(0xFF386A20) else accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

             // Interactive On-Device Pitch Mode Control Center (Slider layout at bottom)
            Card(
                colors = CardDefaults.cardColors(containerColor = GeometricCardBg.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (repetitionHistory.size >= targetSits) {
                        // PHASE 3: CELEBRATION SUMMARY CARD
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GeometricSuccessBg,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "סבב אימון הושלם בהצלחה! 🏆",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "השלמתם $targetSits / $targetSits הושבות מושלמות עבור $dogName עם המאמן $trainerName!",
                                color = GeometricSubtext,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.endSession()
                                    onBack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GeometricSuccessBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("סיום וחזרה למסך הבית", color = Color(0xFF14532D), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (!sessionActive) {
                        // PHASE 1: CAMERA SETUP POSITIONING CARD
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showControlPanel = !showControlPanel }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showControlPanel) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = GeometricSubtext
                            )
                            Text(
                                text = "הכנת מצלמה וכיוון פריים (שלב 1)",
                                color = GeometricAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (showControlPanel) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "כוון את הטלפון כך שהכלב והבעלים יופיעו בפרופיל צד זה לצד זה ללא הסתרה קרובה. הכלב צריך לעמוד בתוך תיבת המיקוד לפני תחילת האימון.",
                                color = GeometricSubtext,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                FilterPill(
                                    label = "כלב בפריים (מדמה)",
                                    active = isDogInFrame,
                                    onClick = { viewModel.toggleCameraDetections() }
                                )
                                FilterPill(
                                    label = "אדם בפריים (מדמה)",
                                    active = isHumanInFrame,
                                    onClick = { viewModel.toggleHumanFrame() }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Box stating indicator state
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDogInFrame) Color(0x2022C55E) else Color(0x20EF4444))
                                    .border(1.dp, if (isDogInFrame) Color(0xFF22C55E) else Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDogInFrame) "כלב בתוך פריים המיקוד (ריבוע ירוק)" else "לא זוהה כלב במיקוד - הזז את המצלמה לכלב (ריבוע אדום)",
                                    color = if (isDogInFrame) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { viewModel.startSession() },
                                enabled = isDogInFrame,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GeometricAccent,
                                    disabledContainerColor = GeometricButtonBg
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "התחל סבב אימון (Start Session)",
                                    color = if (isDogInFrame) GeometricOnAccent else GeometricSubtext,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // PHASE 2: ACTIVE TRAINING SEQUENCE CONTROLS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showControlPanel = !showControlPanel }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showControlPanel) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = GeometricSubtext
                            )
                            Text(
                                text = "לוח בקרה להצגת משקיעים (דמו)",
                                color = GeometricAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (showControlPanel) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                FilterPill(
                                    label = "כלב בפריים",
                                    active = isDogInFrame,
                                    onClick = { viewModel.toggleCameraDetections() }
                                )
                                FilterPill(
                                    label = "אדם בפריים",
                                    active = isHumanInFrame,
                                    onClick = { viewModel.toggleHumanFrame() }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF49454F))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "1. קבע תנוחת כלב",
                                color = GeometricSubtext,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.onDogPoseChanged(DogPosition.STAND) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dogPosition == DogPosition.STAND) GeometricAccent else GeometricButtonBg
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "עומד (STAND)",
                                        fontSize = 11.sp,
                                        color = if (dogPosition == DogPosition.STAND) GeometricOnAccent else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { viewModel.onDogPoseChanged(DogPosition.SIT) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dogPosition == DogPosition.SIT) Color(0xFF4ADE80) else GeometricButtonBg
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "יושב (SIT)",
                                        fontSize = 11.sp,
                                        color = if (dogPosition == DogPosition.SIT) Color(0xFF14532D) else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "2. סמל צעד אילוף ופקודות קוליות",
                                color = GeometricSubtext,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepButton(
                                    label = "הריח חטיף",
                                    step = LureStep.SMELL_TREAT,
                                    current = currentLureStep,
                                    onClick = { viewModel.onTreatSmelled() },
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (sitCommandCount > 1) Color(0x30EF4444) else GeometricButtonBg
                                        )
                                        .border(
                                            1.dp,
                                            if (sitCommandCount > 1) Color(0xFFEF4444) else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.onSitCommandSpoken() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "אמר \"שב\" ($sitCommandCount)",
                                            color = if (sitCommandCount > 1) Color(0xFFFCA5A5) else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                StepButton(
                                    label = "יד מעל הראש",
                                    step = LureStep.HAND_ABOVE_HEAD,
                                    current = currentLureStep,
                                    onClick = { viewModel.onHandLiftedOverhead() },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.onRewardGiven() },
                                colors = ButtonDefaults.buttonColors(containerColor = GeometricAccent),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GeometricOnAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("חלק חטיף וגמול (תגמול כלב)", color = GeometricOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (repetitionHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "היסטוריית חזרות בסבב זה:",
                                    color = GeometricSubtext,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(repetitionHistory.reversed()) { log ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    Color(0x1F475569),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = log.details,
                                                color = GeometricSubtext,
                                                fontSize = 9.sp,
                                                textAlign = TextAlign.Left
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${log.result} ${log.icon}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "#${log.id}",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Support container for Quadruple fields mapping
data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) GeometricAccent.copy(alpha = 0.15f) else GeometricButtonBg)
            .border(
                1.dp,
                if (active) GeometricAccent else Color(0xFF49454F),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) GeometricAccent else GeometricSubtext)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) Color.White else GeometricSubtext,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StepButton(
    label: String,
    step: LureStep,
    current: LureStep,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = current == step
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) GeometricAccent.copy(alpha = 0.2f) else GeometricButtonBg)
            .border(
                1.dp,
                if (active) GeometricAccent else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) GeometricAccent else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
