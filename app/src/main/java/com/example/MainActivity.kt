package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.state.TrainingViewModel
import com.example.ui.TrainingScreen
import com.example.ui.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel: TrainingViewModel = viewModel()
                    val currentScreen = remember { mutableStateOf("WELCOME") }

                    when (currentScreen.value) {
                        "WELCOME" -> WelcomeScreen(
                            viewModel = viewModel,
                            onStartSession = { currentScreen.value = "TRAINING" }
                        )
                        "TRAINING" -> TrainingScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen.value = "WELCOME" }
                        )
                    }
                }
            }
        }
    }
}
