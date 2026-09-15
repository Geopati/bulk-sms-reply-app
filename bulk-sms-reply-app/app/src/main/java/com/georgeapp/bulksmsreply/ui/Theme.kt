package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandBlue = Color(0xFF1565C0)

private val LightColors = lightColorScheme(primary = BrandBlue)
private val DarkColors = darkColorScheme(primary = Color(0xFF90CAF9))

@Composable
fun BulkTextReplyTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
