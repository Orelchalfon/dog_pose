package com.example.state

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DogPosition {
    STAND,
    SIT,
    OUT_OF_FRAME
}

enum class LureStep {
    NONE,
    SMELL_TREAT,
    SPOKEN_COMMAND,
    HAND_ABOVE_HEAD,
    REWARD_GIVEN
}

data class RepetitionLog(
    val id: Int,
    val timestamp: Long,
    val result: String, // Success, Delayed, Repeat Command, Failed Lure
    val icon: String,
    val details: String
)

data class NotificationAlert(
    val text: String,
    val backgroundColorHex: Long, // Hex color value
    val ruleId: Int, // 1 to 4
    val id: Long = System.currentTimeMillis()
)

class TrainingViewModel : ViewModel() {

    // Onboarding / Session Config Settings
    private val _dogName = MutableStateFlow("רקס")
    val dogName: StateFlow<String> = _dogName.asStateFlow()

    private val _trainerName = MutableStateFlow("אוראל")
    val trainerName: StateFlow<String> = _trainerName.asStateFlow()

    private val _targetSits = MutableStateFlow(5)
    val targetSits: StateFlow<Int> = _targetSits.asStateFlow()

    // Session Status
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    // Real-time Detection States
    private val _isDogInFrame = MutableStateFlow(true)
    val isDogInFrame: StateFlow<Boolean> = _isDogInFrame.asStateFlow()

    private val _isHumanInFrame = MutableStateFlow(true)
    val isHumanInFrame: StateFlow<Boolean> = _isHumanInFrame.asStateFlow()

    private val _dogPosition = MutableStateFlow(DogPosition.STAND)
    val dogPosition: StateFlow<DogPosition> = _dogPosition.asStateFlow()

    // Command Tracker
    private val _sitCommandCountInRep = MutableStateFlow(0)
    val sitCommandCountInRep: StateFlow<Int> = _sitCommandCountInRep.asStateFlow()

    // Continuous Lure Sequencing
    private val _currentLureStep = MutableStateFlow(LureStep.NONE)
    val currentLureStep: StateFlow<LureStep> = _currentLureStep.asStateFlow()

    // Notification Banner Overlays (Hebrew)
    private val _activeNotification = MutableStateFlow<NotificationAlert?>(null)
    val activeNotification: StateFlow<NotificationAlert?> = _activeNotification.asStateFlow()

    // History Log
    private val _repetitionHistory = MutableStateFlow<List<RepetitionLog>>(emptyList())
    val repetitionHistory: StateFlow<List<RepetitionLog>> = _repetitionHistory.asStateFlow()

    // Active state tracker
    private var delayCheckerJob: Job? = null
    var sitTimeMillis: Long = 0L
    var isSitRewarded = false

    fun updateConfig(dog: String, trainer: String, count: Int) {
        _dogName.value = dog.ifEmpty { "רקס" }
        _trainerName.value = trainer.ifEmpty { "אוראל" }
        _targetSits.value = count.coerceIn(1, 20)
    }

    // Reset settings & enter Phase 1 alignment pre-session setup
    fun prepareSetup() {
        _sessionActive.value = false
        _sitCommandCountInRep.value = 0
        _currentLureStep.value = LureStep.NONE
        _dogPosition.value = DogPosition.STAND
        _activeNotification.value = null
        _repetitionHistory.value = emptyList()
        isSitRewarded = false
        delayCheckerJob?.cancel()
    }

    fun startSession() {
        _sessionActive.value = true
        _sitCommandCountInRep.value = 0
        _currentLureStep.value = LureStep.NONE
        _dogPosition.value = DogPosition.STAND
        _activeNotification.value = null
        _repetitionHistory.value = emptyList()
        isSitRewarded = false
        
        // Start live ticker to monitor rule deviations like rewarding delay
        startRuleMonitor()
    }

    fun endSession() {
        _sessionActive.value = false
        delayCheckerJob?.cancel()
        _activeNotification.value = null
    }

    // --- Dynamic Detections (triggered by mic, camera, or presentation buttons) ---

    // Trigger Command Input (Word "Sit")
    fun onSitCommandSpoken() {
        if (!_sessionActive.value) return

        // Alert if dog is already sitting when command is issued
        if (_dogPosition.value == DogPosition.SIT) {
            triggerNotification(
                text = "The dog is already sitting",
                backgroundColorHex = 0xFFD0E2FF, // Light blue (info)
                ruleId = 5
            )
            return
        }

        val prevCount = _sitCommandCountInRep.value
        val nextCount = prevCount + 1
        _sitCommandCountInRep.value = nextCount

        // Rule 1: Command repeated more than once
        if (nextCount > 1) {
            triggerNotification(
                text = "פקודה אומרים פעם אחת!", 
                backgroundColorHex = 0xFFFFDADB, // light red
                ruleId = 1
            )
            // Move lure step or update luring failure
            if (_currentLureStep.value == LureStep.SPOKEN_COMMAND) {
                // Rule 2 deviation: repeating sit during lure
                triggerRule2Error("שגיאת פיתוי: חזרה על הפקודה פוגעת בלמידה ומרגילה את הכלב להתעלם!")
            }
        } else {
            // Correct lure step sequence checks
            if (_currentLureStep.value == LureStep.SMELL_TREAT) {
                _currentLureStep.value = LureStep.SPOKEN_COMMAND
            } else if (_currentLureStep.value == LureStep.NONE) {
                // If they say sit before smelling the reward (Rule 2 Deviation)
                _currentLureStep.value = LureStep.SPOKEN_COMMAND
                triggerRule2Error("שגיאת פיתוי: יש לתת לכלב להריח את החטיף לפני הפקודה!")
            }
        }
    }

    // Trigger Dog smells treat
    fun onTreatSmelled() {
        if (!_sessionActive.value) return
        if (_dogPosition.value == DogPosition.SIT) {
            // Already sitting, reset sequence
            resetSequence()
        }
        
        _currentLureStep.value = LureStep.SMELL_TREAT
        _sitCommandCountInRep.value = 0
    }

    // Trigger Lift hand above head
    fun onHandLiftedOverhead() {
        if (!_sessionActive.value) return
        
        val step = _currentLureStep.value
        if (step == LureStep.SPOKEN_COMMAND) {
            _currentLureStep.value = LureStep.HAND_ABOVE_HEAD
        } else {
            // Rule 2 deviation: Moved hand without saying sit or before smelling
            if (step == LureStep.NONE) {
                triggerRule2Error("שגיאת פיתוי: הכלב חייב להריח חטיף קודם, ואז לשמוע פקודה!")
            } else if (step == LureStep.SMELL_TREAT) {
                triggerRule2Error("שגיאת פיתוי: יש לומר את הפקודה \"שב\" לפני הרמת החטיף מעל הראש!")
            }
        }
    }

    // Dog pose transition
    fun onDogPoseChanged(newPose: DogPosition) {
        if (!_sessionActive.value) return
        
        val oldPose = _dogPosition.value
        _dogPosition.value = newPose

        if (newPose == DogPosition.OUT_OF_FRAME) {
            _isDogInFrame.value = false
            return
        } else {
            _isDogInFrame.value = true
        }

        if (oldPose != DogPosition.SIT && newPose == DogPosition.SIT) {
            // Dog has just sat down!
            sitTimeMillis = SystemClock.elapsedRealtime()
            isSitRewarded = false

            // Verify if luring steps prior were fully correct (Rule 2 and Rule 4 check)
            val isLurePerfect = _currentLureStep.value == LureStep.HAND_ABOVE_HEAD

            if (isLurePerfect && _sitCommandCountInRep.value == 1) {
                // Successful Sitting transition (Rule 4)
                triggerNotification(
                    text = "כלב טוב !", 
                    backgroundColorHex = 0xFFDCFCE7, // light green
                    ruleId = 4
                )
            } else {
                // Sitted but with errors / missed lure
                if (_sitCommandCountInRep.value > 1) {
                    // Triggered rule 1 warning previously, but dog still sat
                } else if (_currentLureStep.value != LureStep.HAND_ABOVE_HEAD) {
                    // Say sit was spoken but hand lure was missing (or other order)
                    triggerRule2Error("תנועת הפיתוי חסרה מעל ראש הכלב!")
                }
            }
        }
    }

    // Trigger Reward Given
    fun onRewardGiven() {
        if (!_sessionActive.value) return
        if (_dogPosition.value != DogPosition.SIT) {
            triggerRule2Error("שגיאה לגמול: אין לחלק פרס כשהכלב עומד או מחוץ לטווח!")
            return
        }

        if (!isSitRewarded) {
            isSitRewarded = true
            val responseTime = SystemClock.elapsedRealtime() - sitTimeMillis
            
            // Success condition requires timely reward (< 2s) + correct full sequence
            val isCorrectSequence = _currentLureStep.value == LureStep.HAND_ABOVE_HEAD && _sitCommandCountInRep.value == 1
            val isSuccess = responseTime <= 2000L && isCorrectSequence

            val resultType: String
            val iconStr: String
            val desc: String

            if (isSuccess) {
                // Perfect success! Count the repetition
                resultType = "הצלחה מושלמת"
                iconStr = "🏆"
                desc = "פיתוי ופקודה מדויקים, תגמול מהיר תוך ${(responseTime / 1000.0).format(1)} שניות!"
                
                triggerNotification(
                    text = "כלב טוב !", 
                    backgroundColorHex = 0xFFDCFCE7, // light green
                    ruleId = 4
                )

                // Append Perfect repetition to completed log list
                val logs = _repetitionHistory.value.toMutableList()
                logs.add(
                    RepetitionLog(
                        id = logs.size + 1,
                        timestamp = System.currentTimeMillis(),
                        result = resultType,
                        icon = iconStr,
                        details = desc
                    )
                )
                _repetitionHistory.value = logs
            } else {
                // Sequence or timing mistake identified
                val mistakeReason = when {
                    responseTime > 2000L -> "עיכוב בגמול"
                    _sitCommandCountInRep.value > 1 -> "כפול פקודה"
                    else -> "טעות באופן האילוף"
                }
                resultType = mistakeReason
                iconStr = "❌"
                desc = when {
                    responseTime > 2000L -> "איחור: איך שהכלב עשה פעולה הוא מקבל תגמול"
                    _sitCommandCountInRep.value > 1 -> "פקודה אומרים פעם אחת!"
                    else -> "תנועת הפיתוי חסרה מעל ראש הכלב!"
                }
                
                triggerNotification(
                    text = desc,
                    backgroundColorHex = 0xFFFFE2E2, // soft red container
                    ruleId = 2
                )
            }

            // Postpone sequence reset
            viewModelScope.launch {
                delay(2500)
                resetSequence()
            }
        }
    }

    private fun resetSequence() {
        if (!_sessionActive.value) return
        _sitCommandCountInRep.value = 0
        _currentLureStep.value = LureStep.NONE
        _dogPosition.value = DogPosition.STAND
        _activeNotification.value = null
        isSitRewarded = false
    }

    // Trigger Rule 2 deviations
    private fun triggerRule2Error(errorText: String) {
        triggerNotification(
            text = errorText,
            backgroundColorHex = 0xFFFEE2E2, // soft red container
            ruleId = 2
        )
    }

    private fun triggerNotification(text: String, backgroundColorHex: Long, ruleId: Int) {
        _activeNotification.value = NotificationAlert(
            text = text,
            backgroundColorHex = backgroundColorHex,
            ruleId = ruleId
        )
    }

    fun dismissNotification() {
        _activeNotification.value = null
    }

    // Monitor continuous events (Reward delay ticker)
    private fun startRuleMonitor() {
        delayCheckerJob?.cancel()
        delayCheckerJob = viewModelScope.launch {
            while (_sessionActive.value) {
                delay(400) // check every 400ms
                if (_dogPosition.value == DogPosition.SIT && !isSitRewarded) {
                    val delayTime = SystemClock.elapsedRealtime() - sitTimeMillis
                    // Rule 3 - Owner delays giving reward after dog performs action (2.0 seconds constraint)
                    if (delayTime > 2000L && _activeNotification.value?.ruleId != 3) {
                        triggerNotification(
                            text = "איך שהכלב עשה פעולה הוא מקבל תגמול",
                            backgroundColorHex = 0xFFFFEDD5, // light orange / peach
                            ruleId = 3
                        )
                    }
                }
            }
        }
    }

    // Double extensions
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    fun toggleCameraDetections() {
        _isDogInFrame.value = !_isDogInFrame.value
        if (!_isDogInFrame.value) {
            _dogPosition.value = DogPosition.OUT_OF_FRAME
        } else {
            _dogPosition.value = DogPosition.STAND
        }
    }

    fun toggleHumanFrame() {
        _isHumanInFrame.value = !_isHumanInFrame.value
    }
}
