package com.example.voicepoc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun VoiceChatScreen(vm: VoiceChatViewModel) {
    val msgs by remember { derivedStateOf { vm.messages } }
    val rows = remember(msgs) { msgs.asRowsWithBotTimeStamps() }

    val input by remember { derivedStateOf { vm.input } }
    val listening by remember { derivedStateOf { vm.isListening } }
    val voiceMode by remember { derivedStateOf { vm.voiceSession } }
    val speaking by remember { derivedStateOf { vm.isSpeaking } }
    val VOICE_CONTROLS_PADDING = 112.dp


    // 1) List state + auto-scroll
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }

    Scaffold(
        topBar = { HeaderBarSimple() },
        bottomBar = {
            if (!voiceMode) {
                BottomBar(
                    value = input,
                    onChange = vm::onInputChange,
                    onSend = vm::sendText,
                    onMic = vm::enterVoiceMode
                )
            }
        },
        containerColor = RogersColors.Surface
    ) { pad ->
        val topPad = pad.calculateTopPadding()
        val bottomPad = pad.calculateBottomPadding() + 16.dp
        val extraForVoice = if (voiceMode) VOICE_CONTROLS_PADDING else 0.dp

        val listState = rememberLazyListState()

        // Auto-scroll when data grows OR when entering voice mode
        LaunchedEffect(rows.size, voiceMode) {
            if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad) // keep list below app bar
                    .background(RogersColors.Surface)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = bottomPad + extraForVoice // <-- key change
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = rows,
                    key = {
                        when (it) {
                            is VoiceChatViewModel.ChatRow.Bubble -> it.msg.id
                            is VoiceChatViewModel.ChatRow.TimeStamp -> "ts-${it.hourMin}-${it.sec}-${it.ampm}-${it.hashCode()}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is VoiceChatViewModel.ChatRow.TimeStamp -> TimeStampRow(
                            row.hourMin,
                            row.sec,
                            row.ampm
                        )

                        is VoiceChatViewModel.ChatRow.Bubble -> MessageBubble(row.msg)
                    }
                }
            }

            if (voiceMode) {
                SpeakingControls(
                    listening = listening,
                    speaking = speaking,
                    onMicOrInterrupt = {
                        if (speaking) {
                            vm.cancelTts(); if (!listening) vm.startMic()
                        } else {
                            if (listening) vm.stopMic() else vm.startMic()
                        }
                    },
                    onCancel = { vm.endVoiceMode() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding() // lift above gesture bar
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
    modifier: Modifier = Modifier,
    showMicButton: Boolean = true // set false if you want only the red X
) {
    Row(
        modifier = modifier.navigationBarsPadding(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (showMicButton) {
            // Left: red circular button; when speaking -> animated waves; while listening -> static mic
            RoundRedButton(
                onClick = onMicOrInterrupt,
                showWave = speaking,
                isSpeaking = speaking
            )
            Spacer(Modifier.width(20.dp))
        }

        // Right: red exit button
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFD32F2F))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
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
    val bubbleColor = if (isUser) RogersColors.UserBubble else RogersColors.AgentBubble
    val textColor   = if (isUser) RogersColors.UserText  else RogersColors.AgentText
    val horizontal  = if (isUser) Arrangement.End else Arrangement.Start

    Row(Modifier.fillMaxWidth(), horizontalArrangement = horizontal) {
        Surface(
            color = bubbleColor,
            contentColor = textColor,
            tonalElevation = 0.dp,
            shadowElevation = if (isUser) 2.dp else 1.dp,
            shape = MaterialTheme.shapes.extraLarge // 18dp round
        ) {
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (m.isThinking) TypingDots() else Text(m.text)
            }
        }
    }
}

@Composable
fun TimeStampRow(hourMin: String, sec: String, ampm: String) {
    val text = buildAnnotatedString {
        append(hourMin)            // h:mm
        append(" ")
        withStyle(
            SpanStyle(
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                baselineShift = BaselineShift.Superscript,
                color = Color(0xFF8E8E8E)
            )
        ) { append(sec) }          // ss
        append(ampm)               // " AM"/" PM"
    }

    // left-aligned, same width as bubbles
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 360.dp)   // match MessageBubble max width
                .padding(start = 6.dp)   // small inset like the web chip
        ) {
            Text(
                text = text,
                color = Color(0xFF8E8E8E),
                style = MaterialTheme.typography.labelSmall
            )
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

private fun List<ChatMessage>.asRowsWithBotTimeStamps(): List<VoiceChatViewModel.ChatRow> {
    if (isEmpty()) return emptyList()
    val out = mutableListOf<VoiceChatViewModel.ChatRow>()
    for (m in this) {
        if (m.from == Sender.Bot) {
            val (hm, ss, ap) = splitClock(m.timeMillis)
            out += VoiceChatViewModel.ChatRow.TimeStamp(hm, ss, ap)
        }
        out += VoiceChatViewModel.ChatRow.Bubble(m)
    }
    return out
}

// 3) Formatting helpers
private fun splitClock(t: Long): Triple<String, String, String> {
    val z = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault())
    val hm  = z.format(java.time.format.DateTimeFormatter.ofPattern("h:mm"))   // e.g., 7:50
    val ss  = z.format(java.time.format.DateTimeFormatter.ofPattern("ss"))     // e.g., 10
    val ap  = z.format(java.time.format.DateTimeFormatter.ofPattern(" a"))     // e.g.,  PM
    return Triple(hm, ss, ap)
}

@Composable
private fun AnimatedWaveIcon(
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    isSpeaking: Boolean
) {
    // animate bar heights with phase offsets
    val anim = rememberInfiniteTransition(label = "bars")
    val phases = List(barCount) { i ->
        anim.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900 + i * 90, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar-$i"
        )
    }

    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val gap = w / (barCount * 2f + (barCount - 1))
        val barW = gap * 2f
        val maxH = h
        val minH = h * 0.25f

        for (i in 0 until barCount) {
            val p = phases[i].value
            val t = if (isSpeaking) p else 0.0f
            val bh = minH + (maxH - minH) * t
            val left = i * (barW + gap)
            val top = (h - bh) / 2f
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barW, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

@Composable
private fun RoundRedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWave: Boolean,
    isSpeaking: Boolean
) {
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0xFFD32F2F))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showWave) {
            AnimatedWaveIcon(isSpeaking = isSpeaking)
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.outline_mic_24),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}
