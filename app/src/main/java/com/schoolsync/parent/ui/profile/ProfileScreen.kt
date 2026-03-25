package com.schoolsync.parent.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) onLogout()
    }

    val user = uiState.user

    // ── Logout confirmation dialog ──────────────────────────────────────
    if (uiState.showLogoutDialog) {
        LogoutDialog(
            isLoggingOut = uiState.isLoggingOut,
            onDismiss = { viewModel.setShowLogoutDialog(false) },
            onConfirm = { viewModel.logout() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        if (uiState.isLoading && user == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // ── 1. Circular Avatar ──────────────────────────────────
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circular avatar with gradient border
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(
                                    Brush.linearGradient(listOf(c.accent, c.accentSecondary)),
                                    CircleShape
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!user?.profilePic.isNullOrBlank()) {
                                AsyncImage(
                                    model = user?.profilePic,
                                    contentDescription = "Profile",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(c.accent, c.accentSecondary))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = buildInitials(user?.name ?: ""),
                                        style = TextStyle(
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (c.isDark) c.bgStart else Color.White
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ── 2. Name block ───────────────────────────────
                        Text(
                            text = user?.name ?: "",
                            style = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Email + phone separated by dot
                        val email = user?.email?.takeIf { it.isNotBlank() }
                        val phone = user?.phone?.takeIf { it.isNotBlank() }
                        val subParts = listOfNotNull(email, phone)
                        if (subParts.isNotEmpty()) {
                            Text(
                                text = subParts.joinToString("  \u00B7  "),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = c.textSecondary
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // "Edit profile" pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(c.accentBg)
                                .clickable { /* TODO */ }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit profile",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.accent
                                )
                            )
                        }
                    }
                }

                // ── 3. Stats Row ────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val attendanceValue = if (uiState.attendancePercent > 0f) {
                            "${uiState.attendancePercent.toInt()}%"
                        } else {
                            "-%"
                        }
                        StatPill(
                            value = attendanceValue,
                            label = "Attendance",
                            valueColor = c.success,
                            bgColor = c.successBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            value = "#4",
                            label = "Rank",
                            valueColor = c.info,
                            bgColor = c.infoBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            value = "\u2014 done",
                            label = "Homework",
                            valueColor = c.purple,
                            bgColor = c.purpleBg,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── 4. Student Detail Card ──────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(16.dp)
                            .clickable { /* TODO */ }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: 40dp rounded rect avatar with gradient + initial
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(c.accent, c.accentSecondary))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (user?.name?.firstOrNull()?.uppercase() ?: "?"),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.isDark) c.bgStart else Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Middle: student name + class/section/roll
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user?.name ?: "",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val classSection = buildString {
                                val cls = user?.className?.takeIf { it.isNotBlank() }
                                val sec = user?.section?.takeIf { it.isNotBlank() }
                                val roll = user?.rollNo?.takeIf { it.isNotBlank() }
                                if (cls != null) append("Class $cls")
                                if (sec != null) append("-$sec")
                                if (roll != null) append("  \u00B7  Roll #$roll")
                            }
                            if (classSection.isNotBlank()) {
                                Text(
                                    text = classSection,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = c.textSecondary
                                    )
                                )
                            }
                        }

                        // Right: chevron
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── 5. Settings Group 1 ─────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(16.dp)
                    ) {
                        // Notifications row
                        SettingsRow(
                            emoji = "\uD83D\uDD14",
                            label = "Notifications",
                            subtitle = "Manage alerts",
                            onClick = { /* TODO */ }
                        )

                        SettingsDivider()

                        // Appearance row
                        SettingsRow(
                            emoji = "\uD83C\uDFA8",
                            label = "Appearance",
                            subtitle = when (uiState.themeMode) {
                                "light" -> "Light"
                                "dark" -> "Dark"
                                else -> "System"
                            },
                            onClick = { viewModel.toggleAppearance() }
                        )

                        // Inline theme picker
                        AnimatedVisibility(
                            visible = uiState.showAppearance,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            ThemePicker(
                                currentMode = uiState.themeMode,
                                onModeChange = viewModel::setThemeMode
                            )
                        }

                        SettingsDivider()

                        // Language row
                        SettingsRow(
                            emoji = "\uD83C\uDF10",
                            label = "Language",
                            subtitle = "English",
                            onClick = { /* TODO */ }
                        )
                    }
                }

                // ── 6. Settings Group 2 ─────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(16.dp)
                    ) {
                        // Change password row
                        SettingsRow(
                            emoji = "\uD83D\uDD12",
                            label = "Change password",
                            subtitle = "Update credentials",
                            onClick = { viewModel.toggleChangePassword() }
                        )

                        // Inline change password form
                        AnimatedVisibility(
                            visible = uiState.showChangePassword,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            ChangePasswordForm(
                                currentPassword = uiState.currentPassword,
                                newPassword = uiState.newPassword,
                                confirmPassword = uiState.confirmPassword,
                                onCurrentPasswordChange = viewModel::onCurrentPasswordChange,
                                onNewPasswordChange = viewModel::onNewPasswordChange,
                                onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                                onChangePassword = viewModel::changePassword,
                                isChangingPassword = uiState.isChangingPassword,
                                passwordChangeSuccess = uiState.passwordChangeSuccess,
                                errorMessage = uiState.errorMessage
                            )
                        }

                        SettingsDivider()

                        // Help & FAQ row
                        SettingsRow(
                            emoji = "\u2753",
                            label = "Help & FAQ",
                            subtitle = "Common questions",
                            onClick = { /* TODO */ }
                        )

                        SettingsDivider()

                        // Contact school row
                        SettingsRow(
                            emoji = "\uD83D\uDCDE",
                            label = "Contact school",
                            subtitle = "Call or email",
                            onClick = { /* TODO */ }
                        )

                        SettingsDivider()

                        // About row
                        SettingsRow(
                            emoji = "\uD83D\uDCC4",
                            label = "About",
                            subtitle = "v1.0",
                            onClick = { /* TODO */ }
                        )
                    }
                }

                // ── 7. Logout button ────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setShowLogoutDialog(true) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = c.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log out",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = c.error
                            )
                        )
                    }
                }

                // ── 9. Version ──────────────────────────────────────────
                item {
                    Text(
                        text = "SchoolSync Parent v1.0",
                        style = TextStyle(fontSize = 11.sp, color = c.textTertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun buildInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "?"
    }
}

// ─── Stat Pill ────────────────────────────────────────────────────────────────

@Composable
private fun StatPill(
    value: String,
    label: String,
    valueColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor,
                letterSpacing = (-0.5).sp
            )
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = c.textTertiary,
                letterSpacing = 0.5.sp
            )
        )
    }
}

// ─── Settings Row ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    emoji: String,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = 15.sp),
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary
                )
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = c.textTertiary
                )
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = c.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Settings Divider ─────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider() {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(c.divider)
    )
}

// ─── Theme Picker (inline) ───────────────────────────────────────────────────

@Composable
private fun ThemePicker(
    currentMode: String,
    onModeChange: (String) -> Unit
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "light" to "\u2600\uFE0F Light",
            "dark" to "\uD83C\uDF19 Dark",
            "system" to "\uD83D\uDCF1 System"
        ).forEach { (mode, label) ->
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(c.accentBg)
                                .border(1.dp, c.accent, RoundedCornerShape(14.dp))
                        } else {
                            Modifier
                                .background(Color.Transparent)
                                .border(1.dp, c.divider, RoundedCornerShape(14.dp))
                        }
                    )
                    .clickable { onModeChange(mode) }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) c.accent else c.textTertiary
                    )
                )
            }
        }
    }
}

// ─── Change Password Form ────────────────────────────────────────────────────

@Composable
private fun ChangePasswordForm(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
    isChangingPassword: Boolean,
    passwordChangeSuccess: Boolean,
    errorMessage: String?
) {
    val c = LocalAppColors.current

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        var currentPwdVisible by remember { mutableStateOf(false) }
        var newPwdVisible by remember { mutableStateOf(false) }

        PasswordField(
            value = currentPassword,
            onValueChange = onCurrentPasswordChange,
            label = "Current Password",
            visible = currentPwdVisible,
            onToggleVisibility = { currentPwdVisible = !currentPwdVisible }
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = "New Password",
            visible = newPwdVisible,
            onToggleVisibility = { newPwdVisible = !newPwdVisible }
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Confirm Password",
            visible = newPwdVisible,
            onToggleVisibility = { newPwdVisible = !newPwdVisible }
        )
        Spacer(modifier = Modifier.height(12.dp))

        errorMessage?.let {
            Text(
                text = it,
                style = TextStyle(fontSize = 12.sp, color = c.error),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (passwordChangeSuccess) {
            Text(
                text = "Password changed successfully",
                style = TextStyle(fontSize = 12.sp, color = c.success, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = onChangePassword,
            enabled = !isChangingPassword,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent,
                contentColor = if (c.isDark) c.bgStart else Color.White
            )
        ) {
            if (isChangingPassword) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = if (c.isDark) c.bgStart else Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "Update Password",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

// ─── Password field ──────────────────────────────────────────────────────────

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    val c = LocalAppColors.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, style = TextStyle(fontSize = 12.sp))
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            cursorColor = c.accent,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.glassBorder,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textTertiary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = 14.sp)
    )
}

// ─── Logout Confirmation Dialog ──────────────────────────────────────────────

@Composable
private fun LogoutDialog(
    isLoggingOut: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val c = LocalAppColors.current

    Dialog(
        onDismissRequest = { if (!isLoggingOut) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (c.isDark) c.surfaceElevated else c.surfaceElevated)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Wave emoji
            Text(
                text = "\uD83D\uDC4B",
                style = TextStyle(fontSize = 36.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Log out?",
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You\u2019ll need to sign in again next time.",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel button
                Button(
                    onClick = onDismiss,
                    enabled = !isLoggingOut,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accentBg,
                        contentColor = c.accent
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // Log out button
                Button(
                    onClick = onConfirm,
                    enabled = !isLoggingOut,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.error,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Log out",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}
