package com.schoolsync.parent.ui.auth

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.schoolsync.parent.util.LocaleManager
import com.schoolsync.parent.service.NotificationChannels
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import android.app.Activity
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.R
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (mustChangePassword: Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val c = LocalAppColors.current
    val showDevSettingsState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val showDevSettings = showDevSettingsState.value
    val showForgotState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onLoginSuccess(uiState.mustChangePassword)
    }

    // Hidden dev override dialog — long-press the ZenXii title to
    // open. Survives PC IP changes during testing without rebuilding.
    if (showDevSettings) {
        com.schoolsync.parent.ui.common.DevSettingsDialog(
            devPrefs = viewModel.devPrefs,
            onDismiss = { showDevSettingsState.value = false }
        )
    }

    if (showForgotState.value) {
        ForgotPasswordDialog(onDismiss = { showForgotState.value = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Compact language control, top-right. Not a full picker — see
            // LoginLanguageControl for why.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LoginLanguageControl()
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Logo ──
            Image(
                painter = painterResource(id = R.drawable.zenxii_logo),
                contentDescription = "ZenXii",
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── Brand ── (long-press → hidden dev settings dialog)
            Text(
                text = "ZenXii",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    color = c.textPrimary
                ),
                modifier = Modifier.combinedClickable(
                    // remember-ed: an un-remembered MutableInteractionSource is recreated on
                    // every recomposition and loses its press state.
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { showDevSettingsState.value = true }
                )
            )
            Text(
                text = stringResource(R.string.auth_parent_portal),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.accent,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Login Card ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(20.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.auth_welcome_back),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.auth_sign_in_to_continue),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = c.textSecondary
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Student ID
                OutlinedTextField(
                    value = uiState.userId,
                    onValueChange = viewModel::onUserIdChange,
                    label = { Text(stringResource(R.string.auth_student_id), style = TextStyle(fontSize = 13.sp)) },
                    placeholder = { Text(stringResource(R.string.auth_hint_student_id), style = TextStyle(fontSize = 13.sp)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = loginFieldColors(c),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 14.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.field_password), style = TextStyle(fontSize = 13.sp)) },
                    placeholder = { Text(stringResource(R.string.auth_hint_password), style = TextStyle(fontSize = 13.sp)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(
                                imageVector = if (uiState.passwordVisible)
                                    Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (uiState.passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.login()
                        }
                    ),
                    colors = loginFieldColors(c),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 14.sp)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Error
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    uiState.errorMessage?.let { error ->
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.errorBg)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = error,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = c.error,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                // Sign In Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.login()
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent,
                        contentColor = if (c.isDark) c.bgStart else Color.White,
                        disabledContainerColor = c.accent.copy(alpha = 0.4f),
                        disabledContentColor = if (c.isDark) c.bgStart.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = if (c.isDark) c.bgStart else Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_action_sign_in),
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.material3.TextButton(
                onClick = { showForgotState.value = true }
            ) {
                Text(
                    text = stringResource(R.string.auth_action_forgot_password),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.accent,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun loginFieldColors(c: AppColors) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = c.textPrimary,
    unfocusedTextColor = c.textPrimary,
    cursorColor = c.accent,
    focusedBorderColor = c.accent,
    unfocusedBorderColor = c.glassBorder,
    focusedLabelColor = c.accent,
    unfocusedLabelColor = c.textTertiary,
    focusedPlaceholderColor = c.textTertiary,
    unfocusedPlaceholderColor = c.textTertiary,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

/**
 * Compact language control for the login screen.
 *
 * Deliberately NOT the full picker. The first-run chooser
 * ([com.schoolsync.parent.ui.splash.LanguageSetupScreen]) already asked, and on
 * comparable Indian apps the overwhelming majority of users who pick a language
 * never change it — so showing six options above the login form every time is
 * clutter that competes with the Sign In action.
 *
 * This shows only the CURRENT language, in its own script, and opens a sheet on
 * tap. Same pattern Facebook and Instagram use on their login screens. It still
 * has to exist here, because a parent who reads no English cannot reach Profile
 * to change it, and because the person who installed the app may not be the
 * person using it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginLanguageControl() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val current = LocaleManager.effectiveTag(context)
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, c.divider, RoundedCornerShape(20.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83C\uDF10  " + LocaleManager.labelFor(current),
            style = TextStyle(fontSize = 12.sp, color = c.textSecondary)
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = c.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = c.bgStart
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.lang_sheet_title),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LocaleManager.SUPPORTED.forEach { (tag, endonym) ->
                    val selected = tag == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showSheet = false
                                if (tag != current) {
                                    LocaleManager.setLanguage(context, tag)
                                    NotificationChannels.ensureChannels(context)
                                    (context as? Activity)?.recreate()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = endonym,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) c.accent else c.textPrimary
                            )
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

