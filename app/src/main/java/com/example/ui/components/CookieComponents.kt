package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CookieItem
import com.example.model.CookieLogEntry
import com.example.ui.theme.LocalAppThemePalette

@Composable
fun CookieInspectorPanel(
    cookies: List<CookieItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    logEntries: List<CookieLogEntry>,
    onRefreshCookies: () -> Unit,
    onAddCookieClick: () -> Unit,
    onEditCookieClick: (CookieItem) -> Unit,
    onDeleteCookieClick: (CookieItem) -> Unit,
    onClearAllCookies: () -> Unit,
    onSaveToStorage: () -> Unit,
    onExportJsonClick: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val clipboardManager = LocalClipboardManager.current
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(12.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = palette.surfaceContainer,
            contentColor = palette.primaryAccent,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Çerezler (${cookies.size})") }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Ağ Günlüğü (${logEntries.size})") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedSubTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Çerez adı veya değer ara...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshCookies) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = palette.primaryAccent)
                }
                IconButton(onClick = onAddCookieClick) {
                    Icon(Icons.Default.Add, contentDescription = "Çerez Ekle", tint = palette.primaryAccent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveToStorage,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryAccent,
                        contentColor = palette.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("yt-dlp İçin Kaydet", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onExportJsonClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("JSON Dışa Aktar", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onClearAllCookies) {
                    Text("Temizle", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val cookieListState = rememberLazyListState()
            val cookieWindow = rememberProgressiveWindowState(
                listState = cookieListState,
                totalItemsCount = cookies.size,
                headerItemsOffset = 0,
                initialBatchSize = 6,
                stepBatchSize = 6
            )
            val displayedCookies = remember(cookies, cookieWindow.loadedCount) {
                cookies.take(cookieWindow.loadedCount)
            }

            LazyColumn(
                state = cookieListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = displayedCookies,
                    key = { it.id },
                    contentType = { "cookie_item_row" }
                ) { cookie ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surfaceContainer)
                            .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cookie.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = palette.primaryAccent
                            )
                            Text(
                                text = cookie.value,
                                fontSize = 12.sp,
                                color = palette.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${cookie.domain} • ${cookie.path}",
                                fontSize = 10.sp,
                                color = palette.onSurfaceMuted
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("${cookie.name}=${cookie.value}"))
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = palette.onSurfaceMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { onEditCookieClick(cookie) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Düzenle",
                                tint = palette.secondaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { onDeleteCookieClick(cookie) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Sil",
                                tint = palette.tertiaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            val logListState = rememberLazyListState()
            val logWindow = rememberProgressiveWindowState(
                listState = logListState,
                totalItemsCount = logEntries.size,
                headerItemsOffset = 0,
                initialBatchSize = 6,
                stepBatchSize = 6
            )
            val displayedLogs = remember(logEntries, logWindow.loadedCount) {
                logEntries.take(logWindow.loadedCount)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yakalanan Cookie Başlıkları",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface
                )
                TextButton(onClick = onClearLogs) {
                    Text("Günlüğü Temizle")
                }
            }
            LazyColumn(
                state = logListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = displayedLogs,
                    key = { it.id },
                    contentType = { "cookie_log_row" }
                ) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceContainer)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "[${entry.timestamp}] ${entry.cookieCount} çerez • ${entry.url}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.rawCookieHeader,
                            fontSize = 11.sp,
                            color = palette.onSurfaceMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditCookieDialog(
    initialCookie: CookieItem?,
    defaultDomain: String,
    onDismiss: () -> Unit,
    onSave: (CookieItem) -> Unit
) {
    var name by remember { mutableStateOf(initialCookie?.name ?: "") }
    var value by remember { mutableStateOf(initialCookie?.value ?: "") }
    var domain by remember { mutableStateOf(initialCookie?.domain ?: defaultDomain) }
    var path by remember { mutableStateOf(initialCookie?.path ?: "/") }
    var isSecure by remember { mutableStateOf(initialCookie?.isSecure ?: true) }
    var isHttpOnly by remember { mutableStateOf(initialCookie?.isHttpOnly ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialCookie == null) "Yeni Çerez Ekle" else "Çerezi Düzenle")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Çerez Adı (Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Değer (Value)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Alan Adı (Domain)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Yol (Path)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSecure, onCheckedChange = { isSecure = it })
                    Text("Secure")
                    Spacer(modifier = Modifier.width(16.dp))
                    Checkbox(checked = isHttpOnly, onCheckedChange = { isHttpOnly = it })
                    Text("HttpOnly")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            CookieItem(
                                id = initialCookie?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                value = value.trim(),
                                domain = domain.trim().ifEmpty { ".youtube.com" },
                                path = path.trim().ifEmpty { "/" },
                                isSecure = isSecure,
                                isHttpOnly = isHttpOnly
                            )
                        )
                    }
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun ExportJsonDialog(
    jsonContent: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çerezler JSON Çıktısı") },
        text = {
            OutlinedTextField(
                value = jsonContent,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(jsonContent))
                    onDismiss()
                }
            ) {
                Text("Panoya Kopyala")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}
