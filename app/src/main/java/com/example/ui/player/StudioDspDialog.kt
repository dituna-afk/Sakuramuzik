package com.example.ui.player

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FeatureTogglesConfig
import com.example.player.DEFAULT_EQ_PRESETS
import com.example.player.StudioDspConfig
import com.example.ui.theme.LocalAppThemePalette
import kotlin.math.roundToInt

@Composable
fun StudioDspDialog(
    dspConfig: StudioDspConfig,
    featureToggles: FeatureTogglesConfig,
    onUpdateDspConfig: ((StudioDspConfig) -> StudioDspConfig) -> Unit,
    onUpdateFeatureToggles: ((FeatureTogglesConfig) -> FeatureTogglesConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppThemePalette.current

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
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sakura Stüdyo FX & DSP Merkezi",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.onBackground
                            )
                            Text(
                                text = "Tüm ses efektlerini ayrı ayrı Aç / Kapa yapabilirsiniz",
                                fontSize = 11.sp,
                                color = palette.primaryAccent
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Kapat", fontWeight = FontWeight.Bold, color = palette.primaryAccent)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Quick Master Module Toggles Strip
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surfaceContainer)
                                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Modül Görünürlük & Aktiflik Anahtarları (Aç / Kapa)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = featureToggles.equalizerEnabled,
                                    onClick = {
                                        onUpdateFeatureToggles { it.copy(equalizerEnabled = !it.equalizerEnabled) }
                                    },
                                    label = { Text("Ekolayzer: ${if (featureToggles.equalizerEnabled) "Açık" else "Kapalı"}") }
                                )
                                FilterChip(
                                    selected = featureToggles.pitchAndReverbEnabled,
                                    onClick = {
                                        onUpdateFeatureToggles { it.copy(pitchAndReverbEnabled = !it.pitchAndReverbEnabled) }
                                    },
                                    label = { Text("Slowed/Nightcore: ${if (featureToggles.pitchAndReverbEnabled) "Açık" else "Kapalı"}") }
                                )
                                FilterChip(
                                    selected = featureToggles.spatial8DAudioEnabled,
                                    onClick = {
                                        onUpdateFeatureToggles { it.copy(spatial8DAudioEnabled = !it.spatial8DAudioEnabled) }
                                    },
                                    label = { Text("8D Ses: ${if (featureToggles.spatial8DAudioEnabled) "Açık" else "Kapalı"}") }
                                )
                                FilterChip(
                                    selected = featureToggles.vocalReducerEnabled,
                                    onClick = {
                                        onUpdateFeatureToggles { it.copy(vocalReducerEnabled = !it.vocalReducerEnabled) }
                                    },
                                    label = { Text("Karaoke: ${if (featureToggles.vocalReducerEnabled) "Açık" else "Kapalı"}") }
                                )
                                FilterChip(
                                    selected = featureToggles.ambientMixerEnabled,
                                    onClick = {
                                        onUpdateFeatureToggles { it.copy(ambientMixerEnabled = !it.ambientMixerEnabled) }
                                    },
                                    label = { Text("Ambiyans: ${if (featureToggles.ambientMixerEnabled) "Açık" else "Kapalı"}") }
                                )
                            }
                        }
                    }

                    // 1. 5-Band Graphic Equalizer & Bass Boost
                    if (featureToggles.equalizerEnabled) {
                        item {
                            StudioModuleCard(
                                title = "5-Bant Grafik Ekolayzer & Bass Boost",
                                subtitle = "60Hz - 14kHz frekans kontrolü ve donanım Bass güçlendirici",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = null,
                                        tint = palette.primaryAccent
                                    )
                                },
                                isActive = dspConfig.isEqualizerActive,
                                onActiveChange = { active ->
                                    onUpdateDspConfig { it.copy(isEqualizerActive = active) }
                                }
                            ) {
                                // Presets Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DEFAULT_EQ_PRESETS.forEach { preset ->
                                        FilterChip(
                                            selected = dspConfig.eqPresetName == preset.name,
                                            onClick = {
                                                onUpdateDspConfig {
                                                    it.copy(
                                                        isEqualizerActive = true,
                                                        eqPresetName = preset.name,
                                                        band0Db = preset.bandsDb[0],
                                                        band1Db = preset.bandsDb[1],
                                                        band2Db = preset.bandsDb[2],
                                                        band3Db = preset.bandsDb[3],
                                                        band4Db = preset.bandsDb[4],
                                                        bassBoostStrength = preset.bassStrength
                                                    )
                                                }
                                            },
                                            label = { Text(preset.name, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                val bandLabels = listOf("60 Hz (Alt Bas)", "230 Hz (Bas)", "910 Hz (Orta/Vokal)", "3.6 kHz (Netlik)", "14 kHz (Tiz/Hava)")
                                val bandValues = dspConfig.bandList()

                                bandLabels.forEachIndexed { idx, label ->
                                    val dbVal = bandValues[idx]
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(label, fontSize = 12.sp, color = palette.onSurface)
                                            Text(
                                                text = "${if (dbVal >= 0f) "+" else ""}${dbVal.roundToInt()} dB",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.primaryAccent
                                            )
                                        }
                                        Slider(
                                            value = dbVal,
                                            onValueChange = { newDb ->
                                                onUpdateDspConfig { cfg ->
                                                    when (idx) {
                                                        0 -> cfg.copy(isEqualizerActive = true, eqPresetName = "Özel", band0Db = newDb)
                                                        1 -> cfg.copy(isEqualizerActive = true, eqPresetName = "Özel", band1Db = newDb)
                                                        2 -> cfg.copy(isEqualizerActive = true, eqPresetName = "Özel", band2Db = newDb)
                                                        3 -> cfg.copy(isEqualizerActive = true, eqPresetName = "Özel", band3Db = newDb)
                                                        else -> cfg.copy(isEqualizerActive = true, eqPresetName = "Özel", band4Db = newDb)
                                                    }
                                                }
                                            },
                                            valueRange = -12f..12f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = palette.primaryAccent,
                                                activeTrackColor = palette.primaryAccent
                                            )
                                        )
                                    }
                                }

                                // Bass Boost Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Sub-Bass Güçlendirici (Bass Boost):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.onSurface
                                    )
                                    Text(
                                        text = "%${(dspConfig.bassBoostStrength / 10)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                                Slider(
                                    value = dspConfig.bassBoostStrength.toFloat(),
                                    onValueChange = { strength ->
                                        onUpdateDspConfig {
                                            it.copy(
                                                isEqualizerActive = true,
                                                bassBoostStrength = strength.toInt()
                                            )
                                        }
                                    },
                                    valueRange = 0f..1000f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent
                                    )
                                )
                            }
                        }
                    }

                    // 2. Slowed + Reverb & Nightcore (Pitch & Tempo)
                    if (featureToggles.pitchAndReverbEnabled) {
                        item {
                            StudioModuleCard(
                                title = "Slowed + Reverb & Nightcore",
                                subtitle = "Şarkı hızı (Tempo), ses tonu (Pitch) ve salon yankısı (Reverb)",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.NightlightRound,
                                        contentDescription = null,
                                        tint = palette.primaryAccent
                                    )
                                },
                                isActive = dspConfig.isPitchReverbActive,
                                onActiveChange = { active ->
                                    onUpdateDspConfig { it.copy(isPitchReverbActive = active) }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val modes = listOf(
                                        Triple("Normal", 1.0f to 1.0f, 0),
                                        Triple("Slowed + Reverb", 0.84f to 0.86f, 3),
                                        Triple("Nightcore", 1.24f to 1.22f, 1),
                                        Triple("Deep Slow", 0.90f to 0.78f, 2),
                                        Triple("Konser Salonu", 1.0f to 1.0f, 3)
                                    )
                                    modes.forEach { (name, speedPitch, rev) ->
                                        FilterChip(
                                            selected = dspConfig.pitchReverbPreset == name,
                                            onClick = {
                                                onUpdateDspConfig {
                                                    it.copy(
                                                        isPitchReverbActive = true,
                                                        pitchReverbPreset = name,
                                                        tempoSpeed = speedPitch.first,
                                                        pitchFactor = speedPitch.second,
                                                        reverbLevel = rev
                                                    )
                                                }
                                            },
                                            label = { Text(name, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                // Tempo Speed Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tempo (Hız):", fontSize = 12.sp, color = palette.onSurface)
                                    Text(
                                        text = "${((dspConfig.tempoSpeed * 100).roundToInt() / 100f)}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                                Slider(
                                    value = dspConfig.tempoSpeed,
                                    onValueChange = { spd ->
                                        onUpdateDspConfig {
                                            it.copy(
                                                isPitchReverbActive = true,
                                                pitchReverbPreset = "Özel",
                                                tempoSpeed = spd
                                            )
                                        }
                                    },
                                    valueRange = 0.55f..1.65f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent
                                    )
                                )

                                // Pitch Factor Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pitch (Ses Tonu / Perde):", fontSize = 12.sp, color = palette.onSurface)
                                    Text(
                                        text = "${((dspConfig.pitchFactor * 100).roundToInt() / 100f)}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                                Slider(
                                    value = dspConfig.pitchFactor,
                                    onValueChange = { p ->
                                        onUpdateDspConfig {
                                            it.copy(
                                                isPitchReverbActive = true,
                                                pitchReverbPreset = "Özel",
                                                pitchFactor = p
                                            )
                                        }
                                    },
                                    valueRange = 0.55f..1.65f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent
                                    )
                                )

                                // Reverb Level Selector
                                Text(
                                    text = "Yankı (Reverb) Ortamı:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.onSurface
                                )
                                val reverbNames = listOf("Kapalı", "Küçük Oda", "Orta Salon", "Büyük Salon", "Stüdyo Plate")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    reverbNames.forEachIndexed { idx, rName ->
                                        FilterChip(
                                            selected = dspConfig.reverbLevel == idx,
                                            onClick = {
                                                onUpdateDspConfig {
                                                    it.copy(
                                                        isPitchReverbActive = true,
                                                        reverbLevel = idx
                                                    )
                                                }
                                            },
                                            label = { Text(rName, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 8D / 360° Spatial Audio Panning
                    if (featureToggles.spatial8DAudioEnabled) {
                        item {
                            StudioModuleCard(
                                title = "8D / 360° Uzamsal Ses (Spatial Audio)",
                                subtitle = "Sesi başınızın etrafında 360° döndüren üç boyutlu konser sahnesi",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = palette.primaryAccent
                                    )
                                },
                                isActive = dspConfig.isSpatial8DActive,
                                onActiveChange = { active ->
                                    onUpdateDspConfig { it.copy(isSpatial8DActive = active) }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("360° Dönüş Süresi:", fontSize = 12.sp, color = palette.onSurface)
                                    Text(
                                        text = "${dspConfig.spatialRotationSeconds.roundToInt()} saniye / tur",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                                Slider(
                                    value = dspConfig.spatialRotationSeconds,
                                    onValueChange = { sec ->
                                        onUpdateDspConfig {
                                            it.copy(isSpatial8DActive = true, spatialRotationSeconds = sec)
                                        }
                                    },
                                    valueRange = 3f..16f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Uzamsal Sahne Derinliği:", fontSize = 12.sp, color = palette.onSurface)
                                    Text(
                                        text = "%${dspConfig.spatialDepthStrength / 10}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                                Slider(
                                    value = dspConfig.spatialDepthStrength.toFloat(),
                                    onValueChange = { depth ->
                                        onUpdateDspConfig {
                                            it.copy(isSpatial8DActive = true, spatialDepthStrength = depth.toInt())
                                        }
                                    },
                                    valueRange = 200f..1000f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent
                                    )
                                )
                            }
                        }
                    }

                    // 4. Karaoke / Vocal Reducer Mode
                    if (featureToggles.vocalReducerEnabled) {
                        item {
                            StudioModuleCard(
                                title = "Karaoke / Vokal Azaltıcı Mod",
                                subtitle = "Merkez vokal frekanslarını baskılayarak enstrümanları öne çıkarır",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.MicOff,
                                        contentDescription = null,
                                        tint = palette.primaryAccent
                                    )
                                },
                                isActive = dspConfig.isVocalReducerActive,
                                onActiveChange = { active ->
                                    onUpdateDspConfig { it.copy(isVocalReducerActive = active) }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Vokal Baskılama Şiddeti:", fontSize = 12.sp, color = palette.onSurface)
                                    Text(
                                        text = "%${dspConfig.vocalReductionPercent}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                                Slider(
                                    value = dspConfig.vocalReductionPercent.toFloat(),
                                    onValueChange = { pct ->
                                        onUpdateDspConfig {
                                            it.copy(isVocalReducerActive = true, vocalReductionPercent = pct.toInt())
                                        }
                                    },
                                    valueRange = 25f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent
                                    )
                                )
                            }
                        }
                    }

                    // 5. Background Ambient Sound & Vinyl Crackle Mixer
                    if (featureToggles.ambientMixerEnabled) {
                        item {
                            StudioModuleCard(
                                title = "Arka Plan Ambiyans & Plak Çıtırtısı Mikseri",
                                subtitle = "Müziğin altına gerçek zamanlı yağmur, nostaljik plak çıtırtısı ve rüzgar ekleyin",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Spa,
                                        contentDescription = null,
                                        tint = palette.primaryAccent
                                    )
                                },
                                isActive = dspConfig.isAmbientMixerActive,
                                onActiveChange = { active ->
                                    onUpdateDspConfig { it.copy(isAmbientMixerActive = active) }
                                }
                            ) {
                                AmbientChannelControl(
                                    label = "🌧️ Gece Yağmuru (Soft Rain)",
                                    enabled = dspConfig.rainEnabled,
                                    volume = dspConfig.rainVolume,
                                    onToggle = { en ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, rainEnabled = en) }
                                    },
                                    onVolumeChange = { vol ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, rainEnabled = true, rainVolume = vol) }
                                    }
                                )

                                AmbientChannelControl(
                                    label = "💿 Nostaljik Plak Çıtırtısı (Vinyl Crackle)",
                                    enabled = dspConfig.vinylCrackleEnabled,
                                    volume = dspConfig.vinylCrackleVolume,
                                    onToggle = { en ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, vinylCrackleEnabled = en) }
                                    },
                                    onVolumeChange = { vol ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, vinylCrackleEnabled = true, vinylCrackleVolume = vol) }
                                    }
                                )

                                AmbientChannelControl(
                                    label = "🍃 Sakura Gece Rüzgarı (Night Breeze)",
                                    enabled = dspConfig.nightWindEnabled,
                                    volume = dspConfig.nightWindVolume,
                                    onToggle = { en ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, nightWindEnabled = en) }
                                    },
                                    onVolumeChange = { vol ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, nightWindEnabled = true, nightWindVolume = vol) }
                                    }
                                )

                                AmbientChannelControl(
                                    label = "🔥 Kamp & Şömine Ateşi (Cozy Fire)",
                                    enabled = dspConfig.campfireEnabled,
                                    volume = dspConfig.campfireVolume,
                                    onToggle = { en ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, campfireEnabled = en) }
                                    },
                                    onVolumeChange = { vol ->
                                        onUpdateDspConfig { it.copy(isAmbientMixerActive = true, campfireEnabled = true, campfireVolume = vol) }
                                    }
                                )
                            }
                        }
                    }
                }

                // Bottom Reset & Done Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onUpdateDspConfig { StudioDspConfig() }
                        }
                    ) {
                        Text("Tüm Efektleri Sıfırla", color = palette.onSurfaceMuted)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primaryAccent,
                            contentColor = palette.onPrimary
                        )
                    ) {
                        Text("Tamam", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioModuleCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val palette = LocalAppThemePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceContainer)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) palette.primaryAccent else palette.dividerOrBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isActive) "Açık" else "Kapalı",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) palette.primaryAccent else palette.onSurfaceMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = isActive,
                    onCheckedChange = onActiveChange
                )
            }
        }

        content()
    }
}

@Composable
private fun AmbientChannelControl(
    label: String,
    enabled: Boolean,
    volume: Float,
    onToggle: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val palette = LocalAppThemePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%${(volume * 100).roundToInt()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) palette.primaryAccent else palette.onSurfaceMuted
            )
            Spacer(modifier = Modifier.width(6.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
        if (enabled) {
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0.05f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = palette.primaryAccent,
                    activeTrackColor = palette.primaryAccent
                )
            )
        }
    }
}
