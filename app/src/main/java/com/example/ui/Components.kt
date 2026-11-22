package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.BadgeStyle
import com.example.game.model.KitColors
import com.example.game.model.TeamProfile
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TeamBadge(
    team: TeamProfile,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val primary = Color(team.badgePrimary)
    val accent = Color(team.badgeAccent)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val shieldPath = Path().apply {
                moveTo(w * 0.14f, h * 0.10f)
                lineTo(w * 0.86f, h * 0.10f)
                lineTo(w * 0.86f, h * 0.56f)
                quadraticTo(w * 0.86f, h * 0.84f, w * 0.50f, h * 0.94f)
                quadraticTo(w * 0.14f, h * 0.84f, w * 0.14f, h * 0.56f)
                close()
            }
            drawPath(shieldPath, color = primary)

            when (team.badgeStyle) {
                BadgeStyle.VERTICAL_STRIPES -> {
                    drawRect(
                        color = accent.copy(alpha = 0.88f),
                        topLeft = Offset(w * 0.41f, h * 0.12f),
                        size = Size(w * 0.18f, h * 0.72f)
                    )
                }
                BadgeStyle.HORIZONTAL_BAND -> {
                    drawRect(
                        color = accent,
                        topLeft = Offset(w * 0.15f, h * 0.42f),
                        size = Size(w * 0.70f, h * 0.16f)
                    )
                }
                BadgeStyle.CHEVRON_SHIELD -> {
                    val chev = Path().apply {
                        moveTo(w * 0.18f, h * 0.38f)
                        lineTo(w * 0.50f, h * 0.62f)
                        lineTo(w * 0.82f, h * 0.38f)
                        lineTo(w * 0.82f, h * 0.52f)
                        lineTo(w * 0.50f, h * 0.76f)
                        lineTo(w * 0.18f, h * 0.52f)
                        close()
                    }
                    drawPath(chev, color = accent)
                }
                BadgeStyle.STAR_CREST -> {
                    val cx = w * 0.5f
                    val cy = h * 0.48f
                    val rOut = w * 0.20f
                    val rIn = w * 0.09f
                    val star = Path()
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) rOut else rIn
                        val a = -Math.PI / 2.0 + i * Math.PI / 5.0
                        val px = cx + (cos(a) * r).toFloat()
                        val py = cy + (sin(a) * r).toFloat()
                        if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                    }
                    star.close()
                    drawPath(star, color = accent)
                }
                BadgeStyle.SUN_DISC -> {
                    drawCircle(
                        color = accent,
                        radius = w * 0.21f,
                        center = Offset(w * 0.5f, h * 0.46f)
                    )
                }
            }
            // Crisp outline
            drawPath(
                shieldPath,
                color = Color.White.copy(alpha = 0.65f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.05f)
            )
        }
        Text(
            text = team.shortCode,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.24f).sp
            ),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = size * 0.16f)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp)
        )
    }
}

@Composable
fun KitSwatchPill(
    kit: KitColors,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = ElevatedSlate,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(kit.primaryCompose, kit.secondaryCompose)
                        )
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun BroadcastTopBar(
    title: String,
    onBack: () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BroadcastCharcoal.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ElevatedSlate)
                .testTag("top_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = trailingContent
        )
    }
}

@Composable
fun StadiumActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = PitchEmerald,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BroadcastCharcoal.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .testTag(testTag)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
