package com.example.voicepoc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable
fun VoiceChatScreen(vm: VoiceChatViewModel) {
    val msgs       by remember { derivedStateOf { vm.messages } }
    val input      by remember { derivedStateOf { vm.input } }
    val listening  by remember { derivedStateOf { vm.isListening } }
    val voiceMode  by remember { derivedStateOf { vm.voiceSession } }   // the one new flag
    val speaking by remember { derivedStateOf { vm.isSpeaking } }

    val bottomBarHeight = 76.dp

    Scaffold(
        topBar = { HeaderBarSimple() },

        // Text UI only when NOT in voice session
        bottomBar = {
            if (!voiceMode) {
                BottomBar(
                    value = input,
                    onChange = vm::onInputChange,
                    onSend   = vm::sendText
                )
            }
        },

        // Single FAB to ENTER voice mode
        floatingActionButton = {
            if (!voiceMode) {
                MicFab(
                    listening = listening,
                    speaking  = speaking,
                   // onToggle  = { if (listening) vm.stopMic() else vm.startMic() }
                    onToggle  = { vm.enterVoiceMode() }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = Color(0xFFFAF5FF)
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = if (voiceMode) 16.dp else bottomBarHeight + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(msgs, key = { it.id }) { MessageBubble(it) }
            }

            // Two big buttons shown during the entire session
            // -------- VoiceChatScreen excerpt --------
            if (voiceMode) {
                SpeakingControls(
                    listening = listening,
                    speaking  = speaking,                 // <- keep this
                    onMicOrInterrupt = {
                        if (speaking) { vm.cancelTts(); if (!listening) vm.startMic() }
                        else { if (listening) vm.stopMic() else vm.startMic() }
                    },
                    onCancel = { vm.endVoiceMode() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )

            }
        }
    }
}

@Composable
private fun MicFab(
    listening: Boolean,
    speaking: Boolean,
    onToggle: () -> Unit
) {
    // Pulse for listening
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val container = when {
        speaking  -> Color(0xFFD32F2F)                     // red while TTS speaking
        listening -> MaterialTheme.colorScheme.primary      // same color, but with pulse ring
        else      -> MaterialTheme.colorScheme.primary
    }

    Box {
        // Soft pulsing ring behind the FAB only while listening
        if (listening) {
            val extra = (10 + 6 * pulse).dp
            Box(
                Modifier
                    .matchParentSize()
                    .padding(end = 0.dp) // keep aligned to FAB
            )
            // simple halo
            Box(
                Modifier
                    .size(56.dp + extra)           // grow/shrink
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(container.copy(alpha = 0.15f))
            )
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = container
        ) {
            val icon = if (listening) Icons.Default.Close
            else ImageVector.vectorResource(id = R.drawable.outline_mic_24)
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun SpeakingControls(
    listening: Boolean,
    speaking: Boolean,
    onMicOrInterrupt: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse the left FAB while listening or speaking (barge-in available)
    val scale by rememberInfiniteTransition(label = "pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 1.10f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

    Row(
        modifier = modifier.navigationBarsPadding(),
        horizontalArrangement = Arrangement.Center
    ) {
        val pulseMod =
            if (listening || speaking) Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            else Modifier

        // LEFT: Mic / Stop / Wave(Interrupt)
        FloatingActionButton(
            onClick = onMicOrInterrupt,
            containerColor = Color(0xFF6750A4),
            modifier = pulseMod.size(64.dp)
        ) {
            val icon = when {
                speaking  -> ImageVector.vectorResource(R.drawable.outline_waves_24) // interrupt
                listening -> ImageVector.vectorResource(R.drawable.outline_mic_24)                                      // stop listening
                else      -> ImageVector.vectorResource(R.drawable.outline_mic_24)     // start listening
            }
            Icon(icon, contentDescription = null, tint = Color.White)
        }

        Spacer(Modifier.width(28.dp))

        // RIGHT: Exit voice mode
        FloatingActionButton(
            onClick = onCancel,
            containerColor = Color(0xFFB3261E),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Exit voice mode", tint = Color.White)
        }
    }
}

@Composable
fun HeaderBarSimple() {
    Surface(color = Color(0xFFD32F2F)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Rogers Assist",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            // optional icons (settings, etc.)
        }
    }
}

@Composable
fun MessageBubble(m: ChatMessage) {
    val isUser = m.from == Sender.User
    val bubbleColor = if (isUser) Color(0xFFF9F9F9) else Color(0xFFEDEDED)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 360.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (m.isThinking) TypingDots() else Text(m.text)
        }
    }
}

@Composable
fun TypingDots() {
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300) // dot animation speed
            step = (step + 1) % 3
        }
    }

    val dots = when (step) {
        0 -> "•"
        1 -> "• •"
        else -> "• • •"
    }

    Text(text = dots, color = Color.Gray)
}
