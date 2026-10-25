package com.example.ui.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.downloader.MediaTrackInfo
import com.example.model.LyricLine
import com.example.ui.theme.LocalAppThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LyricCardStyle(
    val displayName: String,
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val textColor: Color
) {
    SAKURA_NIGHT(
        displayName = "Sakura Gece",
        bgTop = Color(0xFF2B0918),
        bgBottom = Color(0xFF0E0408),
        accent = Color(0xFFFF6FA5),
        textColor = Color(0xFFFFF0F5)
    ),
    NEON_CYBER(
        displayName = "Neon Kiraz",
        bgTop = Color(0xFF1A0B2E),
        bgBottom = Color(0xFF090414),
        accent = Color(0xFF00F0FF),
        textColor = Color(0xFFFFFFFF)
    ),
    GOLDEN_VINYL(
        displayName = "Altın Plak",
        bgTop = Color(0xFF261C10),
        bgBottom = Color(0xFF120C06),
        accent = Color(0xFFFFC857),
        textColor = Color(0xFFFFF8E7)
    ),
    CRIMSON_VELVET(
        displayName = "Kızıl Kadife",
        bgTop = Color(0xFF3B0910),
        bgBottom = Color(0xFF140205),
        accent = Color(0xFFFF4D6D),
        textColor = Color(0xFFFFE5EC)
    )
}

@Composable
fun LyricCardGeneratorDialog(
    track: MediaTrackInfo?,
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    onDisableModule: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppThemePalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedIndices = remember {
        mutableStateListOf<Int>().apply {
            if (activeLyricIndex in lyrics.indices) {
                add(activeLyricIndex)
                if (activeLyricIndex + 1 in lyrics.indices) {
                    add(activeLyricIndex + 1)
                }
            } else if (lyrics.isNotEmpty()) {
                add(0)
            }
        }
    }

    var selectedStyle by remember { mutableStateOf(LyricCardStyle.SAKURA_NIGHT) }
    var includeTurkishTranslation by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    val selectedLines = selectedIndices.sorted().mapNotNull { lyrics.getOrNull(it) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.5.dp, palette.primaryAccent.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
            color = palette.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Şarkı Sözü Kartı Tasarımcısı",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.onBackground
                            )
                            Text(
                                text = "1-4 satır seçip görsel kart olarak kaydedin veya paylaşın",
                                fontSize = 11.sp,
                                color = palette.primaryAccent
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Kapat", fontWeight = FontWeight.Bold, color = palette.primaryAccent)
                    }
                }

                // Live Card Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(selectedStyle.bgTop, selectedStyle.bgBottom)
                            )
                        )
                        .border(1.5.dp, selectedStyle.accent.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "❝",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedStyle.accent
                        )

                        if (selectedLines.isEmpty()) {
                            Text(
                                text = "Aşağıdaki listeden paylaşmak istediğiniz şarkı sözü satırlarına dokunun...",
                                fontSize = 14.sp,
                                color = selectedStyle.textColor.copy(alpha = 0.7f)
                            )
                        } else {
                            selectedLines.forEach { line ->
                                Text(
                                    text = line.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedStyle.textColor
                                )
                                if (includeTurkishTranslation && !line.translatedText.isNullOrBlank()) {
                                    Text(
                                        text = line.translatedText,
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = selectedStyle.accent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.title ?: "Sakura Music",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedStyle.textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track?.artist ?: "Sanatçı",
                                    fontSize = 11.sp,
                                    color = selectedStyle.accent,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "🌸 SAKURA MUSIC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = selectedStyle.accent
                            )
                        }
                    }
                }

                // Style & Translation Options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricCardStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Türkçe Çeviriyi Karta Dahil Et",
                        fontSize = 12.sp,
                        color = palette.onSurface
                    )
                    Switch(
                        checked = includeTurkishTranslation,
                        onCheckedChange = { includeTurkishTranslation = it }
                    )
                }

                // Selectable Lyric Lines List
                Text(
                    text = "Kartta Görünecek Satırları Seçin (${selectedIndices.size}/4):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(lyrics) { idx, line ->
                        val isSelected = idx in selectedIndices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) palette.primaryAccent.copy(alpha = 0.22f)
                                    else palette.surfaceContainer
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (isSelected) {
                                        selectedIndices.remove(idx)
                                    } else if (selectedIndices.size < 4) {
                                        selectedIndices.add(idx)
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.onSurface
                                )
                                if (!line.translatedText.isNullOrBlank()) {
                                    Text(
                                        text = line.translatedText,
                                        fontSize = 11.sp,
                                        color = palette.primaryAccent
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Seçili",
                                    tint = palette.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDisableModule()
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("Modülü Kapat", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (selectedLines.isEmpty() || isExporting) return@Button
                            isExporting = true
                            scope.launch {
                                val uri = saveLyricCardToGallery(
                                    context = context,
                                    track = track,
                                    lines = selectedLines,
                                    style = selectedStyle,
                                    includeTranslation = includeTurkishTranslation
                                )
                                isExporting = false
                                if (uri != null) {
                                    Toast.makeText(
                                        context,
                                        "Şarkı sözü kartı Galeriye kaydedildi!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Kart kaydedilemedi.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = selectedLines.isNotEmpty() && !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.surfaceContainer,
                            contentColor = palette.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galeriye Kaydet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (selectedLines.isEmpty() || isExporting) return@Button
                            isExporting = true
                            scope.launch {
                                val uri = saveLyricCardToGallery(
                                    context = context,
                                    track = track,
                                    lines = selectedLines,
                                    style = selectedStyle,
                                    includeTranslation = includeTurkishTranslation
                                )
                                isExporting = false
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "🌸 ${track?.artist ?: ""} - ${track?.title ?: ""}\n" +
                                                selectedLines.joinToString("\n") { it.text }
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Şarkı Sözü Kartını Paylaş")
                                    )
                                }
                            }
                        },
                        enabled = selectedLines.isNotEmpty() && !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primaryAccent,
                            contentColor = palette.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paylaş", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private suspend fun saveLyricCardToGallery(
    context: Context,
    track: MediaTrackInfo?,
    lines: List<LyricLine>,
    style: LyricCardStyle,
    includeTranslation: Boolean
): android.net.Uri? = withContext(Dispatchers.IO) {
    try {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                style.bgTop.toArgb(),
                style.bgBottom.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative Card Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 6f
            color = style.accent.toArgb()
            alpha = 180
        }
        canvas.drawRoundRect(RectF(48f, 48f, width - 48f, height - 48f), 56f, 56f, borderPaint)

        // Quote Mark
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accent.toArgb()
            textSize = 110f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("❝", 100f, 190f, quotePaint)

        // Lyrics Text
        val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor.toArgb()
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val transTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accent.toArgb()
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        var currentY = 300f
        val maxTextWidth = width - 200f

        for (line in lines) {
            val wrappedMain = wrapTextLines(line.text, mainTextPaint, maxTextWidth)
            for (wLine in wrappedMain) {
                canvas.drawText(wLine, 100f, currentY, mainTextPaint)
                currentY += 64f
            }
            if (includeTranslation && !line.translatedText.isNullOrBlank()) {
                val wrappedTrans = wrapTextLines(line.translatedText, transTextPaint, maxTextWidth)
                for (tLine in wrappedTrans) {
                    canvas.drawText(tLine, 100f, currentY, transTextPaint)
                    currentY += 50f
                }
            }
            currentY += 34f
        }

        // Footer Track Info
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor.toArgb()
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accent.toArgb()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val songTitle = (track?.title ?: "Sakura Music").take(38)
        val songArtist = (track?.artist ?: "Sakura Player").take(42)

        canvas.drawText(songTitle, 100f, height - 170f, titlePaint)
        canvas.drawText(songArtist, 100f, height - 115f, artistPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accent.toArgb()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("SAKURA MUSIC", width - 100f, height - 115f, brandPaint)

        val fileName = "Sakura_Lyric_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SakuraMusic")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, contentValues) ?: return@withContext null
        resolver.openOutputStream(itemUri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, doneValues, null, null)
        }
        itemUri
    } catch (_: Exception) {
        null
    }
}

private fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""
    for (word in words) {
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(candidate) <= maxWidth) {
            currentLine = candidate
        } else {
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine)
    return lines.ifEmpty { listOf(text) }
}
