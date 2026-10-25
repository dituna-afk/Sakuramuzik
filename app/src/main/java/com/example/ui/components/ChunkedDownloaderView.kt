package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.downloader.DownloadProgress
import com.example.ui.theme.LocalAppThemePalette

@Composable
fun ChunkedDownloaderCard(
    progress: DownloadProgress,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceContainer)
            .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "4 Kanallı Paralel Akış İndirici",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Text(
                    text = progress.statusMessage,
                    fontSize = 12.sp,
                    color = palette.onSurfaceMuted
                )
            }

            if (progress.isDownloading) {
                OutlinedButton(onClick = onCancelDownload) {
                    Icon(Icons.Default.Close, contentDescription = "İptal")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("İptal")
                }
            } else {
                Button(
                    onClick = onStartDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryAccent,
                        contentColor = palette.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = "İndir")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Akışı İndir")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress.overallFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = palette.primaryAccent,
            trackColor = palette.surfaceVariant
        )

        if (progress.chunks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                progress.chunks.forEach { chunk ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kanal #${chunk.index + 1}",
                            fontSize = 10.sp,
                            color = palette.onSurfaceMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(chunk.progressFraction)
                                    .background(palette.secondaryAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}
