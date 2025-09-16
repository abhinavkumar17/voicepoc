package com.example.voicepoc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

@Composable
fun BottomBar(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit = {}     // NEW: default so call sites don’t break
) {
    Surface(shadowElevation = 12.dp, color = RogersColors.Surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded input with trailing icon INSIDE
            Surface(
                color = RogersColors.InputBg,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        textStyle = LocalTextStyle.current.copy(color = RogersColors.AgentText),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text("Type your message", color = Color(0xFF9A9A9A))
                            }
                            inner()
                        }
                    )

                    // Trailing icon: Send when text present; otherwise Mic
                    val showSend = value.isNotBlank()
                    IconButton(
                        onClick = { if (showSend) onSend() else onMic() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (showSend) RogersColors.Red else RogersColors.Red)
                    ) {
                        Icon(
                            imageVector = if (showSend) Icons.Default.Send else ImageVector.vectorResource(id = R.drawable.outline_mic_24),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}