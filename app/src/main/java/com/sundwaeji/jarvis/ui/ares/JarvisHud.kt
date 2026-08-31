package com.sundwaeji.jarvis.ui.ares

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Cyan = Color(0xFF55E7FF)
private val Ice = Color(0xFFD9FAFF)
private val Navy = Color(0xFF030A12)

@Composable
fun JarvisHud(state: JarvisUiState, onMic: () -> Unit, onOverlay: () -> Unit, onSystem: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state)
        Spacer(Modifier.height(18.dp)); Telemetry(state); Spacer(Modifier.weight(.7f))
        JarvisCore(state.phase, state.audioLevel, state.activeTool, Modifier.size(260.dp))
        Spacer(Modifier.height(18.dp))
        Text(state.phase.label, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(22.dp)); JarvisWaveform(state.audioLevel, state.phase in setOf(JarvisPhase.LISTENING, JarvisPhase.SPEAKING))
        Spacer(Modifier.weight(.55f)); SubtitleCard(state.koreanSubtitle, state.overlayAuthorized, onOverlay); Spacer(Modifier.height(18.dp))
        CommandBar(onMic, onSystem, state.backgroundServiceRunning)
    }
}

@Composable
private fun Header(state: JarvisUiState) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Column {
        Text("J.A.R.V.I.S", color = Ice, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
        Text("PERSONAL INTELLIGENCE SYSTEM", color = Cyan.copy(alpha = .58f), fontSize = 8.sp, letterSpacing = 1.5.sp)
    }
    Text(state.phase.label, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Telemetry(state: JarvisUiState) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    TelemetryValue("BAT", state.batteryPercent?.let { "$it%" } ?: "--")
    TelemetryValue("NET", state.networkLabel); TelemetryValue("AI", state.phase.label); TelemetryValue("MIC", if (state.phase == JarvisPhase.LISTENING) "ACTIVE" else "READY")
}

@Composable
private fun TelemetryValue(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Cyan.copy(alpha = .5f), fontSize = 8.sp, letterSpacing = 1.sp)
    Text(value, color = Ice, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun JarvisCore(phase: JarvisPhase, audioLevel: Float, activeTool: String?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "core")
    val accent = when (phase) {
        JarvisPhase.ERROR -> Color(0xFFFF6178)
        JarvisPhase.EXECUTING -> Color(0xFFFFC46B)
        JarvisPhase.THINKING, JarvisPhase.TRANSLATING -> Color(0xFF9D91FF)
        else -> Cyan
    }
    val rotationDuration = when (phase) {
        JarvisPhase.LISTENING, JarvisPhase.SPEAKING -> 2800
        JarvisPhase.THINKING, JarvisPhase.TRANSLATING -> 1600
        JarvisPhase.IDLE -> 11000
        else -> 6500
    }
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(rotationDuration, easing = LinearEasing)), label = "rotation")
    val pulse by transition.animateFloat(.88f, 1.04f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }) {
            val c = center
            drawCircle(accent.copy(.12f), size.minDimension * .48f, c, style = Stroke(1.dp.toPx()))
            drawArc(accent.copy(.7f), -55f, 105f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawArc(accent.copy(.32f), 130f, 72f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(accent.copy(.25f), size.minDimension * .34f, c, style = Stroke(1.dp.toPx()))
            repeat(8) { i ->
                val angle = Math.toRadians((i * 45).toDouble()); val r = size.minDimension * .43f
                drawCircle(if (activeTool != null && i == 1) accent else Cyan.copy(.5f), 2.5.dp.toPx(), Offset(c.x + kotlin.math.cos(angle).toFloat() * r, c.y + kotlin.math.sin(angle).toFloat() * r))
            }
        }
        Box(Modifier.size(118.dp).graphicsLayer { scaleX = pulse + audioLevel * .08f; scaleY = pulse + audioLevel * .08f }.clip(CircleShape).background(accent.copy(.08f)).border(1.dp, accent.copy(.72f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Ice).border(7.dp, accent.copy(.45f), CircleShape))
        }
        if (activeTool != null) Text(activeTool, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun JarvisWaveform(audioLevel: Float, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "wavePhase")
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        val mid = size.height / 2
        repeat(64) { i ->
            val x = size.width * i / 63
            val amplitude = if (active) kotlin.math.sin(i * .56f + phase) * (6f + audioLevel * 18f) else kotlin.math.sin(i * .35f) * 2f
            drawLine(Cyan.copy(if (active) .78f else .25f), Offset(x, mid - amplitude), Offset(x, mid + amplitude), 1.4.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SubtitleCard(text: String, overlayAuthorized: Boolean, onOverlay: () -> Unit) = Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xB20A1722)).border(1.dp, Cyan.copy(.28f), RoundedCornerShape(12.dp)).padding(16.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("JARVIS // KO SUBTITLE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(
            if (overlayAuthorized) "OVERLAY ON" else "ENABLE OVERLAY",
            color = if (overlayAuthorized) Cyan else Ice.copy(.62f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOverlay).padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
    Spacer(Modifier.height(7.dp))
    AnimatedContent(targetState = text, transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(90)) }, label = "subtitle") { subtitle ->
        Text(subtitle, color = Ice, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun CommandBar(onMic: () -> Unit, onSystem: () -> Unit, serviceRunning: Boolean) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
    Command(Icons.Outlined.Mic, "MIC", onMic); Command(Icons.Outlined.Visibility, "VISION") {}
    Command(Icons.Outlined.Chat, "CHAT") {}; Command(if (serviceRunning) Icons.Outlined.StopCircle else Icons.Outlined.Settings, "SYSTEM", onSystem)
}

@Composable
private fun Command(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) = Column(Modifier.clickable(onClick = onClick).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(icon, label, tint = Cyan, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(5.dp))
    Text(label, color = Ice.copy(.7f), fontSize = 8.sp, letterSpacing = 1.sp)
}
