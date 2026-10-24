// Requirements: projection and source packing controls; display-mode selection has been removed.
// Open decisions/research: source auto-detection and non-equirectangular projections are deferred.
// Risks/verification: source eye selection, half-packing aspect and settings must not change Activity orientation.
package org.grinch27.vrviewer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.Projection
import org.grinch27.vrviewer.core.StereoLayout
import org.grinch27.vrviewer.playback.PlaybackStatus

fun projectionLabel(value: Projection) = when (value) {
    Projection.FLAT -> "平面"
    Projection.EQUIRECT_180 -> "180° 等距柱状"
    Projection.EQUIRECT_360 -> "360° 等距柱状"
}
fun stereoLabel(value: StereoLayout) = when (value) {
    StereoLayout.MONO -> "单目片源"
    StereoLayout.LEFT_RIGHT -> "左右 SBS"
    StereoLayout.TOP_BOTTOM -> "上下 TB"
}
fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    profile: PlaybackProfile,
    status: PlaybackStatus,
    headAvailable: Boolean,
    onProfile: (PlaybackProfile) -> Unit,
    onSpeed: (Float) -> Unit,
    onLoop: (Boolean) -> Unit,
    onRecenter: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("观看设置", style = MaterialTheme.typography.titleLarge)
            Text("投影", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(Projection.entries, profile.projection, ::projectionLabel) { onProfile(profile.copy(projection = it)) }
            Text("片源排布", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(StereoLayout.entries, profile.stereo, ::stereoLabel) { onProfile(profile.copy(stereo = it)) }
            if (profile.stereo != StereoLayout.MONO) {
                ToggleRow("使用右眼画面", profile.rightEye) { onProfile(profile.copy(rightEye = it)) }
                if (profile.projection == Projection.FLAT) ToggleRow("半宽 / 半高压缩片源", profile.halfPacked) { onProfile(profile.copy(halfPacked = it)) }
            }
            if (profile.projection != Projection.FLAT) {
                ToggleRow(if (headAvailable) "陀螺仪控制" else "设备无姿态传感器，使用拖动", profile.headTracking && headAvailable, headAvailable) { onProfile(profile.copy(headTracking = it)) }
                Text("垂直视野 ${profile.fieldOfView.toInt()}°", style = MaterialTheme.typography.labelLarge)
                Slider(value = profile.fieldOfView, onValueChange = { onProfile(profile.copy(fieldOfView = it)) }, valueRange = 35f..110f)
                OutlinedButton(onClick = onRecenter) { Text("视角回正") }
            }
            HorizontalDivider()
            Text("播放速度", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf(.5f, 1f, 1.25f, 1.5f, 2f), status.speed, { "${it}×" }, onSpeed)
            ToggleRow("单片循环", status.loop, onChange = onLoop)
            Text("180°/360°仅支持等距柱状投影，不包含鱼眼、立方体或特殊网格。SBS 默认显示左图，TB 默认显示上图，可切换使用右眼画面。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) }) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
