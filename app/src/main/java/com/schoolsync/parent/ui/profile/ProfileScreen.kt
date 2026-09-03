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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.res.stringResource
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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.parent.R
import com.schoolsync.parent.service.NotificationChannels
import com.schoolsync.parent.util.LocaleManager
import com.schoolsync.parent.ui.components.ClassTeacherCard
import com.schoolsync.parent.ui.components.bouncyClickable
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToMyTeachers: () -> Unit = {},
    onOpenHomework: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Phase 9b: refresh attendance % every time Profile is shown
    // so it stays in sync with the Attendance tab and Dashboard.
    LaunchedEffect(Unit) {
        viewModel.refreshAttendance()
    }

    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) onLogout()
    }

    val user = uiState.user
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var profileExpanded by remember { mutableStateOf(false) }
    var helpExpanded by remember { mutableStateOf(false) }
    var contactExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

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
                state = listState,
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
                                    contentDescription = stringResource(R.string.drawer_profile),
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

                        Spacer(modifier = Modifier.height(4.dp))
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
                            label = stringResource(R.string.drawer_attendance),
                            valueColor = c.success,
                            bgColor = c.successBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            value = "#4",
                            label = stringResource(R.string.field_rank),
                            valueColor = c.info,
                            bgColor = c.infoBg,
                            modifier = Modifier.weight(1f)
                        )
                        val pending = uiState.pendingHomeworkCount
                        val homeworkValue = when {
                            !uiState.homeworkLoaded -> "\u2014"
                            pending == 0 -> stringResource(R.string.profile_all_done)
                            else -> pending.toString()
                        }
                        StatPill(
                            value = homeworkValue,
                            label = if (uiState.homeworkLoaded && pending > 0) stringResource(R.string.common_pending) else stringResource(R.string.drawer_homework),
                            valueColor = c.purple,
                            bgColor = c.purpleBg,
                            modifier = Modifier.weight(1f),
                            // Pending \u2192 jump into the Pending tab; all done \u2192 open
                            // the full homework list.
                            onClick = { onOpenHomework(if (pending > 0) "pending" else "all") }  // i18n-ignore: tab keys
                        )
                    }
                }

                // ── 4. Student Detail Card (expandable) ────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(16.dp)
                    ) {
                        // Header row (always visible)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { profileExpanded = !profileExpanded }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(c.accent, c.accentSecondary))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (user?.name?.firstOrNull()?.uppercase() ?: "?"),
                                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (c.isDark) c.bgStart else Color.White)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.name ?: "",
                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val classSection = buildString {
                                    val cls = user?.className?.takeIf { it.isNotBlank() }
                                    val sec = user?.section?.takeIf { it.isNotBlank() }
                                    val roll = user?.rollNo?.takeIf { it.isNotBlank() }
                                    if (cls != null) append(cls)
                                    if (sec != null) append(" - $sec")
                                    if (roll != null) append(stringResource(R.string.profile_roll_suffix, roll.toString()))
                                }
                                if (classSection.isNotBlank()) {
                                    Text(text = classSection, style = TextStyle(fontSize = 11.sp, color = c.textSecondary))
                                }
                            }
                            Icon(
                                imageVector = if (profileExpanded) Icons.Filled.Visibility else Icons.Filled.ChevronRight,
                                contentDescription = if (profileExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                                tint = c.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Expanded student details
                        AnimatedVisibility(visible = profileExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                // Personal Information
                                ProfileSectionHeader(text = stringResource(R.string.profile_section_personal), color = c.accent)
                                ProfileDetailRow(label = stringResource(R.string.profile_dob), value = user?.dob, color = c)
                                ProfileDetailRow(label = stringResource(R.string.field_gender), value = user?.gender, color = c)
                                ProfileDetailRow(label = stringResource(R.string.profile_admission_date), value = user?.admissionDate, color = c)
                                ProfileDetailRow(label = stringResource(R.string.auth_student_id), value = user?.userId, color = c)

                                Spacer(modifier = Modifier.height(10.dp))

                                // Contact Information
                                ProfileSectionHeader(text = stringResource(R.string.profile_section_contact), color = c.accent)
                                ProfileDetailRow(label = stringResource(R.string.field_phone), value = user?.phone, color = c)
                                ProfileDetailRow(label = stringResource(R.string.field_email), value = user?.email, color = c)

                                Spacer(modifier = Modifier.height(10.dp))

                                // Family Information
                                ProfileSectionHeader(text = stringResource(R.string.profile_section_family), color = c.accent)
                                ProfileDetailRow(label = stringResource(R.string.profile_father_name), value = user?.fatherName, color = c)
                                ProfileDetailRow(label = stringResource(R.string.profile_mother_name), value = user?.motherName, color = c)

                                Spacer(modifier = Modifier.height(10.dp))

                                // School Information
                                ProfileSectionHeader(text = stringResource(R.string.profile_section_school), color = c.accent)
                                ProfileDetailRow(label = stringResource(R.string.field_school), value = user?.schoolDisplayName, color = c)
                                ProfileDetailRow(label = stringResource(R.string.field_session), value = user?.session, color = c)

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // ── 4b. Class Teacher contact card ──────────────────────
                // Pinned directly below the student detail card so the
                // parent's primary point of contact is one tap away. Hidden
                // when no Active class teacher is assigned.
                if (uiState.classTeacher != null) {
                    item {
                        ClassTeacherCard(
                            entry = uiState.classTeacher!!,
                            subjects = uiState.classTeacherSubjects,
                            onMessage = onNavigateToMyTeachers
                        )
                    }
                } else if (uiState.classTeacherLoading) {
                    // Skeleton while the first fetch resolves — reserves the
                    // card's space so nothing pops in / shifts on open.
                    item { ClassTeacherSkeleton() }
                }

                // ── 5. Settings Group 1 ─────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(16.dp)
                    ) {
                        // Notifications row — opens the OS app-notification
                        // settings page so the parent can toggle channels.
                        SettingsRow(
                            emoji = "\uD83D\uDD14",
                            label = stringResource(R.string.section_notifications),
                            subtitle = stringResource(R.string.profile_manage_alerts),
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Fallback: open app details page.
                                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(fallback)
                                }
                            }
                        )

                        SettingsDivider()

                        // Appearance row
                        SettingsRow(
                            emoji = "\uD83C\uDFA8",
                            label = stringResource(R.string.section_appearance),
                            subtitle = when (uiState.themeMode) {
                                "light" -> stringResource(R.string.theme_light)
                                "dark" -> stringResource(R.string.theme_dark)
                                else -> stringResource(R.string.theme_system)
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

                        // Language row. The subtitle shows the CURRENT language in
                        // its own script, so it is readable to the person who set it.
                        SettingsRow(
                            emoji = "🌐",
                            label = stringResource(R.string.settings_language),
                            subtitle = LocaleManager.labelFor(
                                LocaleManager.effectiveTag(context)
                            ),
                            onClick = { viewModel.toggleLanguage() }
                        )

                        AnimatedVisibility(
                            visible = uiState.showLanguage,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            LanguagePicker(
                                currentTag = LocaleManager.effectiveTag(context),
                                onSelect = { tag ->
                                    if (tag != LocaleManager.effectiveTag(context)) {
                                        // 1. Persist locally with commit() — recreate()
                                        //    is about to read this back synchronously.
                                        LocaleManager.setLanguage(context, tag)
                                        // 2. Re-create the notification channels so their
                                        //    names/descriptions come back in the new
                                        //    language; the OS caches the strings it was
                                        //    given at creation time.
                                        NotificationChannels.ensureChannels(context)
                                        // 3. Tell the server, without waiting for it.
                                        viewModel.mirrorLanguage(tag)
                                        // 4. Re-run attachBaseContext with the new tag.
                                        (context as? Activity)?.recreate()
                                    }
                                }
                            )
                        }
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
                            label = stringResource(R.string.profile_action_change_password),
                            subtitle = stringResource(R.string.profile_update_credentials),
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

                        // Help & FAQ row — expands with common questions.
                        SettingsRow(
                            emoji = "\u2753",
                            label = stringResource(R.string.profile_action_help_faq),
                            subtitle = stringResource(R.string.profile_common_questions),
                            onClick = { helpExpanded = !helpExpanded }
                        )
                        AnimatedVisibility(
                            visible = helpExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            HelpFaqContent()
                        }

                        SettingsDivider()

                        // Contact school row — expands with school details
                        // and quick call/email actions.
                        SettingsRow(
                            emoji = "\uD83D\uDCDE",
                            label = stringResource(R.string.profile_action_contact_school),
                            subtitle = user?.schoolDisplayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_call_or_email),
                            onClick = { contactExpanded = !contactExpanded }
                        )
                        AnimatedVisibility(
                            visible = contactExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            ContactSchoolContent(
                                schoolName = user?.schoolDisplayName ?: "",
                                schoolId = user?.schoolId ?: user?.schoolCode ?: "",
                                onCall = { phone ->
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                onEmail = { email ->
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                        }

                        SettingsDivider()

                        // About row — expands with app information.
                        SettingsRow(
                            emoji = "\uD83D\uDCC4",
                            label = stringResource(R.string.section_about),
                            subtitle = stringResource(R.string.profile_app_version),
                            onClick = { aboutExpanded = !aboutExpanded }
                        )
                        AnimatedVisibility(
                            visible = aboutExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            AboutContent()
                        }
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
                            text = stringResource(R.string.profile_action_log_out),
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
                        text = stringResource(R.string.profile_app_version),
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
private fun ClassTeacherSkeleton() {
    val c = LocalAppColors.current
    val block = c.shimmerBase.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(block)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(block)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(block)
            )
        }
    }
}

@Composable
private fun StatPill(
    value: String,
    label: String,
    valueColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = LocalAppColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            maxLines = 1,
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
            .bouncyClickable(onClick = onClick, pressedScale = 0.985f)
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

/**
 * Language picker.
 *
 * A vertical list, not the 3-across [ThemePicker] row: six options in Tamil,
 * Telugu and Devanagari will not fit side by side on a 5-inch screen, and this
 * is exactly the clipping this program has to avoid rather than create.
 *
 * Every option is labelled by its **endonym** — its name in its own language.
 * A parent who reads only Gujarati cannot find the word "Gujarati" in a list,
 * but can find "ગુજરાતી". For the same reason the labels are NOT translated
 * strings: they are identical in every locale.
 */
@Composable
private fun LanguagePicker(
    currentTag: String,
    onSelect: (String) -> Unit
) {
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocaleManager.SUPPORTED.forEach { (tag, endonym) ->
            val isSelected = currentTag == tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    .clickable { onSelect(tag) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = endonym,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) c.accent else c.textPrimary
                    )
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
            label = stringResource(R.string.profile_current_password),
            visible = currentPwdVisible,
            onToggleVisibility = { currentPwdVisible = !currentPwdVisible }
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = stringResource(R.string.profile_new_password),
            visible = newPwdVisible,
            onToggleVisibility = { newPwdVisible = !newPwdVisible }
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = stringResource(R.string.profile_confirm_password),
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
                text = stringResource(R.string.profile_msg_password_changed),
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
                    stringResource(R.string.profile_update_password),
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
                text = stringResource(R.string.profile_dialog_log_out_title),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.profile_logout_msg),
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
                        text = stringResource(R.string.common_cancel),
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
                            text = stringResource(R.string.profile_action_log_out),
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

// ─── Profile Detail Helpers ──────────────────────────────────────────────────

@Composable
private fun ProfileSectionHeader(text: String, color: Color) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.8.sp
        ),
        modifier = Modifier.padding(bottom = 6.dp, top = 2.dp)
    )
}

@Composable
private fun ProfileDetailRow(label: String, value: String?, color: AppColors) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = TextStyle(fontSize = 12.sp, color = color.textTertiary),
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = value,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color.textPrimary),
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}

// ─── Help & FAQ (inline expanded content) ────────────────────────────────────

@Composable
private fun HelpFaqContent() {
    val c = LocalAppColors.current
    // Each answer is ONE resource, not concatenated fragments: adjacent literals
    // cannot be reordered, and word order differs in every target language.
    val faqs = listOf(
        stringResource(R.string.faq_q_fees) to stringResource(R.string.faq_a_fees),
        stringResource(R.string.faq_q_attendance) to stringResource(R.string.faq_a_attendance),
        stringResource(R.string.faq_q_homework) to stringResource(R.string.faq_a_homework),
        stringResource(R.string.faq_q_push) to stringResource(R.string.faq_a_push),
        stringResource(R.string.faq_q_password) to stringResource(R.string.faq_a_password)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 12.dp)
    ) {
        faqs.forEachIndexed { index, (q, a) ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = q,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            )
            if (a.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = a,
                    style = TextStyle(fontSize = 11.sp, color = c.textSecondary, lineHeight = 15.sp)
                )
            }
        }
    }
}

// ─── Contact School (inline expanded content) ────────────────────────────────

@Composable
private fun ContactSchoolContent(
    schoolName: String,
    schoolId: String,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit
) {
    val c = LocalAppColors.current
    // Fetch school contact info from Firestore once the expansion opens.
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(schoolId.isNotBlank()) }

    LaunchedEffect(schoolId) {
        if (schoolId.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("schools").document(schoolId).get()
            snap.addOnSuccessListener { doc ->
                phone = doc.getString("phone")
                    ?: doc.getString("contactPhone")
                    ?: doc.getString("contact_phone") ?: ""
                email = doc.getString("email")
                    ?: doc.getString("contactEmail")
                    ?: doc.getString("contact_email") ?: ""
                address = doc.getString("address") ?: ""
                loading = false
            }.addOnFailureListener { loading = false }
        } catch (_: Exception) { loading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 12.dp)
    ) {
        if (schoolName.isNotBlank()) {
            Text(
                text = schoolName,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (address.isNotBlank()) {
            Text(
                text = address,
                style = TextStyle(fontSize = 11.sp, color = c.textSecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        when {
            loading -> {
                Text(
                    stringResource(R.string.profile_loading_contact),
                    style = TextStyle(fontSize = 11.sp, color = c.textTertiary)
                )
            }
            phone.isBlank() && email.isBlank() -> {
                Text(
                    stringResource(R.string.profile_no_contact),
                    style = TextStyle(fontSize = 11.sp, color = c.textSecondary, lineHeight = 15.sp)
                )
            }
            else -> {
                if (phone.isNotBlank()) {
                    ContactActionRow(
                        emoji = "\uD83D\uDCDE",
                        label = stringResource(R.string.profile_action_call_office),
                        value = phone,
                        onClick = { onCall(phone) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (email.isNotBlank()) {
                    ContactActionRow(
                        emoji = "\u2709\uFE0F",
                        label = stringResource(R.string.profile_action_email_office),
                        value = email,
                        onClick = { onEmail(email) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactActionRow(
    emoji: String,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.accentBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, style = TextStyle(fontSize = 14.sp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.textTertiary)
            )
            Text(
                text = value,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── About (inline expanded content) ─────────────────────────────────────────

@Composable
private fun AboutContent() {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_app_name),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.profile_version_fmt, "1.0", "2026.04"),
            style = TextStyle(fontSize = 10.sp, color = c.textTertiary, letterSpacing = 0.3.sp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.profile_about_tagline),
            style = TextStyle(fontSize = 11.sp, color = c.textSecondary, lineHeight = 16.sp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        AboutLine("\uD83D\uDCDD", stringResource(R.string.profile_about_realtime))
        AboutLine("\uD83D\uDCB3", stringResource(R.string.profile_about_payments))
        AboutLine("\uD83D\uDCE2", stringResource(R.string.profile_about_notices))
        AboutLine("\uD83D\uDD10", stringResource(R.string.profile_about_private_prefix, stringResource(R.string.profile_about_privacy)))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.profile_copyright),
            style = TextStyle(fontSize = 10.sp, color = c.textTertiary)
        )
    }
}

@Composable
private fun AboutLine(emoji: String, text: String) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = emoji, style = TextStyle(fontSize = 11.sp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = TextStyle(fontSize = 11.sp, color = c.textSecondary, lineHeight = 15.sp)
        )
    }
}
