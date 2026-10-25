package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.CookieInspectorScreen
import com.example.ui.theme.LocalAppThemePalette
import com.example.viewmodel.CookieViewModel

data class PresetSite(
    val name: String,
    val url: String
)

@Composable
fun CookieAppScreen(
    viewModel: CookieViewModel,
    onBackToMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToMusic)
    val palette = LocalAppThemePalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackToMusic) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = palette.onBackground
                )
            }
            Text(
                text = "Çerez Denetleyici (yt-dlp Netscape Cookie)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        CookieInspectorScreen(
            cookieViewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
