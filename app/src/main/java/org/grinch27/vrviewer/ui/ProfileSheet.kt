// Requirements: zoom for every projection; independent horizontal/vertical FOV; recenter next to reset in settings.
// Open decisions/research: gesture sensitivity and device comfort need user feedback.
// Risks/verification: accept only positive finite zoom; reset view without changing source, progress or orientation.
package org.grinch27.vrviewer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.Projection
import org.grinch27.vrviewer.core.ProjectionMath
import org.grinch27.vrviewer.core.StereoLayout
import org.grinch27.vrviewer.playback.PlaybackStatus
import java.util.Locale

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
    viewportAspect: Float,
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
            }
            HorizontalDivider()
            ZoomSetting(profile.zoom) { onProfile(profile.copy(zoom = it)) }
            Text("画面旋转 ${angleLabel(profile.rotationDegrees)}°；双指扭转可调整，单指可拖动。", style = MaterialTheme.typography.bodySmall)
            if (profile.projection != Projection.FLAT) {
                val horizontal = ProjectionMath.horizontalFov(profile, viewportAspect)
                val tangents = ProjectionMath.viewTangents(profile, viewportAspect)
                Text("基础 FOV（缩放前）", style = MaterialTheme.typography.titleSmall)
                Text("水平 FOV ${angleLabel(horizontal)}°", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = horizontal.coerceIn(PlaybackProfile.MIN_FOV, PlaybackProfile.MAX_FOV),
                    onValueChange = { onProfile(profile.copy(horizontalFieldOfView = it)) },
                    valueRange = PlaybackProfile.MIN_FOV..PlaybackProfile.MAX_FOV,
                )
                Text("垂直 FOV ${angleLabel(profile.fieldOfView)}°", style = MaterialTheme.typography.labelLarge)
                Slider(value = profile.fieldOfView, onValueChange = { onProfile(profile.copy(fieldOfView = it)) }, valueRange = PlaybackProfile.MIN_FOV..PlaybackProfile.MAX_FOV)
                Text("当前视场：水平 ${angleLabel(ProjectionMath.fovFromTangent(tangents.first))}°，垂直 ${angleLabel(ProjectionMath.fovFromTangent(tangents.second))}°。两轴独立调整，画面比例也会随之变化。", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("平面视频使用缩放比例；水平/垂直 FOV 在 180°/360°投影中调整。", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRecenter) { Text("回正") }
                OutlinedButton(onClick = {
                    onProfile(profile.copy(zoom = 1f, fieldOfView = 80f, horizontalFieldOfView = ProjectionMath.horizontalFov(PlaybackProfile(), viewportAspect), rotationDegrees = 0f, panX = 0f, panY = 0f))
                    onRecenter()
                }) { Text("重置观看参数") }
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

private fun angleLabel(degrees: Float): String = String.format(
    Locale.ROOT,
    if (degrees != 0f && (kotlin.math.abs(degrees) < .01f || kotlin.math.abs(degrees) > 179.99f)) "%.4f" else "%.2f",
    degrees,
)

@Composable
private fun ZoomSetting(zoom: Float, onZoom: (Float) -> Unit) {
    var text by remember(zoom) { mutableStateOf(zoom.toString()) }
    val value = text.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("缩放比例（倍）") },
        supportingText = { Text("三指捏合调整，也可输入大于 0 的倍数。") },
        singleLine = true,
        isError = text.isNotBlank() && value == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { value?.let(onZoom) }),
        trailingIcon = { TextButton(onClick = { value?.let(onZoom) }, enabled = value != null) { Text("应用") } },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { onZoom(ProjectionMath.scaledZoom(zoom, .8f)) }) { Text("缩小") }
        TextButton(onClick = { onZoom(1f) }) { Text("恢复 1×") }
        TextButton(onClick = { onZoom(ProjectionMath.scaledZoom(zoom, 1.25f)) }) { Text("放大") }
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
