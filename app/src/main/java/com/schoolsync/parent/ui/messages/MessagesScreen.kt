package com.schoolsync.parent.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.gradientBackground

/**
 * MessagesScreen — Coming Soon variant.
 *
 * Direct messaging has been deferred while a modern in-app messaging
 * experience is designed. This screen renders a polished placeholder
 * so parents see a clear product state rather than a dormant chat list.
 * Existing conversation history is preserved server-side in Firestore
 * (`conversations`, `messages`, `messageInboxes` collections) and will
 * become accessible again when messaging returns.
 *
 * Signature is intentionally kept backwards-compatible with the previous
 * chat screen so the navigation graph composable call site does not
 * change. The `onChatViewChange` callback is fired once with `false` so
 * the parent navigation can keep the global bottom bar visible.
 *
 * This Composable does NOT instantiate `MessagesViewModel`, so no
 * RTDB or chat-backend traffic is generated when the screen opens.
 */
@Composable
fun MessagesScreen(
    onChatViewChange: (Boolean) -> Unit = {}
) {
    DisposableEffect(Unit) {
        onChatViewChange(false)
        onDispose { onChatViewChange(false) }
    }

    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hero icon circle
            Surface(
                shape = CircleShape,
                color = appColors.accent.copy(alpha = 0.12f),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(112.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = appColors.accent,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // "Coming Soon" pill badge
            Surface(
                shape = RoundedCornerShape(50),
                color = appColors.accent.copy(alpha = 0.12f),
            ) {
                Text(
                    text = "COMING SOON",
                    color = appColors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Messaging Coming Soon",
                color = appColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Body copy
            Text(
                text = "We're building a better way to talk with your school. " +
                        "Until it launches, please check Notices and Circulars for " +
                        "updates from teachers and admin. For anything urgent, please " +
                        "contact your school directly.",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Preservation note
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Existing conversations are safely preserved and will become " +
                            "available again when messaging returns.",
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
