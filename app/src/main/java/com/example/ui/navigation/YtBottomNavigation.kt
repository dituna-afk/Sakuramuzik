package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.animation.sakuraPressScale
import com.example.ui.theme.LocalAppThemePalette
import com.example.viewmodel.YtMusicTab

data class NavItem(
    val tab: YtMusicTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun YtBottomNavigationBar(
    selectedTab: YtMusicTab,
    onTabSelected: (YtMusicTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val items = androidx.compose.runtime.remember {
        listOf(
            NavItem(YtMusicTab.HOME, "Ana Sayfa", Icons.Default.Home, "nav_home"),
            NavItem(YtMusicTab.SEARCH, "Keşfet", Icons.Default.Search, "nav_search"),
            NavItem(YtMusicTab.LIBRARY, "Kitaplık", Icons.Default.LibraryMusic, "nav_library"),
            NavItem(YtMusicTab.PLAYLISTS, "Listeler", Icons.AutoMirrored.Filled.QueueMusic, "nav_playlists"),
            NavItem(YtMusicTab.SETTINGS, "Ayarlar", Icons.Default.Settings, "nav_settings")
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface.copy(alpha = 0.96f))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = palette.dividerOrBorder.copy(alpha = 0.6f), thickness = 0.8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = selectedTab == item.tab
                val bgColor = if (isSelected) {
                    palette.primaryAccent.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                }
                val contentColor = if (isSelected) {
                    palette.primaryAccent
                } else {
                    palette.onSurfaceMuted
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .sakuraPressScale()
                        .clickable {
                            if (!isSelected) {
                                onTabSelected(item.tab)
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
