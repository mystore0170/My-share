package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NearbyDevice
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.TealAccent
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RadarScannerView(
    userDeviceName: String,
    discoveredDevices: List<NearbyDevice>,
    onDeviceClick: (NearbyDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarAnimations")

    // Pulsing outer wave 1
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )

    // Pulsing outer wave 2
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    // Pulsing outer wave 3
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse3"
    )

    // Sweep radar rotation
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepRotation"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Radar Concentric Circles Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = minOf(size.width, size.height) * 0.44f

            // Background static concentric rings
            val ringCount = 4
            for (i in 1..ringCount) {
                val r = (maxRadius / ringCount) * i
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.15f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // Animated expanding pulse circles
            drawCircle(
                color = ElectricBlueLight.copy(alpha = (1f - pulse1).coerceIn(0f, 1f) * 0.45f),
                radius = maxRadius * pulse1,
                center = center,
                style = Stroke(width = 3f)
            )

            drawCircle(
                color = ElectricBlueLight.copy(alpha = (1f - pulse2).coerceIn(0f, 1f) * 0.45f),
                radius = maxRadius * pulse2,
                center = center,
                style = Stroke(width = 2.5f)
            )

            drawCircle(
                color = TealAccent.copy(alpha = (1f - pulse3).coerceIn(0f, 1f) * 0.45f),
                radius = maxRadius * pulse3,
                center = center,
                style = Stroke(width = 2f)
            )

            // Radar beam sweep line
            val rad = Math.toRadians(sweepAngle.toDouble())
            val lineEnd = Offset(
                x = center.x + (maxRadius * cos(rad)).toFloat(),
                y = center.y + (maxRadius * sin(rad)).toFloat()
            )

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(ElectricBlueLight.copy(alpha = 0.8f), Color.Transparent),
                    start = center,
                    end = lineEnd
                ),
                start = center,
                end = lineEnd,
                strokeWidth = 2.5f
            )
        }

        // Center Device (Current User Phone)
        Surface(
            modifier = Modifier
                .size(76.dp)
                .shadow(12.dp, CircleShape)
                .testTag("center_user_device"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "My Device",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
        }

        // Orbiting Discovered Devices
        discoveredDevices.forEachIndexed { index, device ->
            // Distribute discovered devices across radar radius and angles
            val angle = device.angleRad + (index * 1.2f)
            val radiusPx = 110.dp.value * (1f + (index % 3) * 0.4f)
            val offsetX = (radiusPx * cos(angle.toDouble())).roundToInt()
            val offsetY = (radiusPx * sin(angle.toDouble())).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .testTag("device_node_${device.endpointId}"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .clickable { onDeviceClick(device) },
                    shape = CircleShape,
                    color = if (device.isConnecting) TealAccent else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (device.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = device.name,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (device.isConnecting) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
