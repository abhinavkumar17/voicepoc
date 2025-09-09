package com.example.voicepoc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RogersRed = Color(0xFFD32F2F)
private val RogersRedDark = Color(0xFFB71C1C)
private val BubbleUser = Color(0xFFF9F9F9)      // light grey
private val BubbleBot = Color(0xFFEDEDED)       // slightly darker grey
private val SurfaceSoft = Color(0xFFFAF5FF)     // very soft tint

val UserBubbleColor get() = BubbleUser
val BotBubbleColor get() = BubbleBot
val HeaderColor get() = RogersRed

private val lightColors = lightColorScheme(
    primary = RogersRed,
    onPrimary = Color.White,
    surface = SurfaceSoft,
    background = SurfaceSoft
)

@Composable
fun VoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) lightColors else lightColors,
        typography = Typography(),
        content = content
    )
}
