package com.example.voicepoc

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask mic permission once.
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* no-op */ }.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            MaterialTheme {
                val vm = remember { VoiceChatViewModel("9ej15092ItVqZipccKKb27XvHGuq5YXDYhAXZxcCRB0jQSmFxIvkJQQJ99BIACYeBjFXJ3w3AAAYACOGzc1d", "eastus") }
                VoiceChatScreen(vm)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
