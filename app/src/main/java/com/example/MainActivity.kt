package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.KeySyncApp
import com.example.ui.KeySyncViewModel

// Force cache rebuild comment
class MainActivity : ComponentActivity() {
    private val viewModel: KeySyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeySyncApp(viewModel = viewModel)
        }
    }
}

