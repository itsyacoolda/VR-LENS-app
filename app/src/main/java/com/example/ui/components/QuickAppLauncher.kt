package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VrCardBorder
import com.example.ui.theme.VrCardSurface
import com.example.ui.theme.VrCyanNeon
import com.example.ui.theme.VrTextPrimary
import com.example.ui.theme.VrTextSecondary
import com.example.ui.theme.VrVioletNeon

data class AppShortcut(
    val name: String,
    val packageName: String,
    val fallbackUrl: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAppLauncher(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val apps = listOf(
        AppShortcut("YouTube", "com.google.android.youtube", "https://m.youtube.com", Icons.Default.PlayCircle, Color(0xFFFF0000)),
        AppShortcut("Chrome", "com.android.chrome", "https://google.com", Icons.Default.Language, Color(0xFF4285F4)),
        AppShortcut("Netflix", "com.netflix.mediaclient", "https://netflix.com", Icons.Default.Movie, Color(0xFFE50914)),
        AppShortcut("RetroArch", "com.retroarch", "https://www.retroarch.com", Icons.Default.Gamepad, Color(0xFF10B981)),
        AppShortcut("Twitch", "tv.twitch.android.app", "https://twitch.tv", Icons.Default.PlayCircle, Color(0xFF9146FF)),
        AppShortcut("VLC Player", "org.videolan.vlc", "https://www.videolan.org", Icons.Default.Movie, Color(0xFFFF9800))
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VrCardSurface)
            .border(1.dp, VrCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = VrVioletNeon,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch App Into VR SBS",
                    style = MaterialTheme.typography.titleSmall,
                    color = VrTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Touch Pass Active",
                style = MaterialTheme.typography.bodySmall,
                color = VrCyanNeon,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap any app to open it with the VR SBS overlay running over it:",
            style = MaterialTheme.typography.bodySmall,
            color = VrTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                AppChip(app = app) {
                    launchTargetApp(context, app)
                }
            }
        }
    }
}

@Composable
private fun AppChip(
    app: AppShortcut,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(app.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = app.icon,
                contentDescription = app.name,
                tint = app.color,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyMedium,
            color = VrTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Launch,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(12.dp)
        )
    }
}

private fun launchTargetApp(context: Context, app: AppShortcut) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)

    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        Toast.makeText(context, "Opening ${app.name} with VR SBS Overlay", Toast.LENGTH_SHORT).show()
    } else {
        // Fallback to web version
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(app.fallbackUrl))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open ${app.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
