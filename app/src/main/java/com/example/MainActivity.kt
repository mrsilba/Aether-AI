package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aether.minimal.ui.MinimalWorkstationScreen
import com.aether.ui.WorkstationViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WorkstationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinimalWorkstationScreen(viewModel = viewModel)
        }
    }
}
