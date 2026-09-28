package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.state.TrainingViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    viewModel: TrainingViewModel,
    onStartSession: () -> Unit
) {
    val dogName by viewModel.dogName.collectAsState()
    val trainerName by viewModel.trainerName.collectAsState()
    val targetSits by viewModel.targetSits.collectAsState()

    var tempDogName by remember { mutableStateOf(dogName) }
    var tempTrainerName by remember { mutableStateOf(trainerName) }
    var tempTargetSits by remember { mutableStateOf(targetSits) }

    val scrollState = rememberScrollState()

    // Geometric Balance elegant dark background gradient
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(GeometricBackground, GeometricCardBg)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Branding Header - Geometric balance badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(GeometricAccent.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .border(2.dp, GeometricAccent, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = "App logo",
                    tint = GeometricAccent,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DOG POSE TRAINER",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "עוזר אימון כלבים חכם בזמן אמת",
                color = GeometricSubtext,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Info Card: Target Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = GeometricCardBg),
                shape = RoundedCornerShape(24.dp), // MD3 rounded shapes
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "הגדרות סבב אימון",
                        color = GeometricAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dog Name Input
                    OutlinedTextField(
                        value = tempDogName,
                        onValueChange = { tempDogName = it },
                        label = { Text("שם הכלב", color = GeometricSubtext) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeometricAccent,
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = GeometricAccent,
                            unfocusedLabelColor = GeometricSubtext,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = { Icon(Icons.Default.Pets, contentDescription = "", tint = GeometricSubtext) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Trainer Name Input
                    OutlinedTextField(
                        value = tempTrainerName,
                        onValueChange = { tempTrainerName = it },
                        label = { Text("שם המאמן", color = GeometricSubtext) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeometricAccent,
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = GeometricAccent,
                            unfocusedLabelColor = GeometricSubtext,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = { Icon(Icons.Default.Person, contentDescription = "", tint = GeometricSubtext) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Target Sits Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(GeometricButtonBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$tempTargetSits חזרות",
                                color = GeometricAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "יעד הושבות לסבב",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = tempTargetSits.toFloat(),
                        onValueChange = { tempTargetSits = it.toInt() },
                        valueRange = 1f..15f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = GeometricAccent,
                            activeTrackColor = GeometricAccent,
                            inactiveTrackColor = Color(0xFF49454F)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pedagogical Training Rules Grid
            Text(
                text = "חוקי בית הספר לאילוף כלבים",
                color = GeometricSubtext,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Right
            )

            RuleItem(
                number = "1",
                icon = Icons.Default.VolumeUp,
                title = "אומרים את הפקודה פעם אחת",
                description = "חזרה על המילה 'שב' מספר פעמים גורמת לכלב לפתח חסינות ולהתעלם מהפעם הראשונה.",
                accentColor = Color(0xFFFCA5A5)
            )

            RuleItem(
                number = "2",
                icon = Icons.Default.BackHand,
                title = "פיתוי נכון עם החטיף",
                description = "מציגים את החטיף לכלב להרחה, אומרים 'שב' פעם אחת ומרימים את היד מעל ראשו עד שהוא מתיישב.",
                accentColor = GeometricAccent
            )

            RuleItem(
                number = "3",
                icon = Icons.Default.Timer,
                title = "גמול מיידי בהתיישבות",
                description = "יש לתת את החטיף מיד כשהישבן של הכלב נוגע ברצפה. עיכוב של מעל 3 שניות מבלבל אותו.",
                accentColor = Color(0xFFFDBA74)
            )

            RuleItem(
                number = "4",
                icon = Icons.Default.CheckCircle,
                title = "שיבוח קולי מיידי",
                description = "שבחו את הכלב מיד ב- 'כלב טוב!' בקול שמח וגבוה כדי לחזק את התגובה החיובית.",
                accentColor = GeometricSuccessBg
            )

            Spacer(modifier = Modifier.height(110.dp))
        }

        // Floating Action Start CTA Button matching Geometric Balance lavender block
        Button(
            onClick = {
                viewModel.updateConfig(tempDogName, tempTrainerName, tempTargetSits)
                viewModel.prepareSetup()
                onStartSession()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GeometricAccent),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth()
                .height(56.dp)
                .border(2.dp, GeometricAccent, RoundedCornerShape(28.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = GeometricOnAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "התחל סבב אימון פעיל",
                    color = GeometricOnAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RuleItem(
    number: String,
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GeometricCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
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
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = GeometricSubtext,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Right
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
