package com.schoolsync.parent.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
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
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
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
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.gradientBackground

/**
 * Phase A Part 2 — forced password-change screen.
 *
 * Shown after a parent's first login when the linked students doc has
 * `mustChangePassword: true`. Replaces the auto-generated password
 * shipped via SMS at enrollment time. After a successful change the
 * NavGraph routes the user on to the main dashboard.
 */
@Composable
fun ForceChangePasswordScreen(
    onPasswordChanged: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ForceChangePasswordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val c = LocalAppColors.current
    val context = LocalContext.current

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            // Surface a confirmation toast before navigating off-screen
            // so the parent has visible proof their password was saved
            // (without it the screen flips to Dashboard with no feedback
            // and they wonder if anything happened).
            Toast.makeText(
                context,
                "Password changed successfully",
                Toast.LENGTH_LONG,
            ).show()
            kotlinx.coroutines.delay(900) // let the toast appear
            onPasswordChanged()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Capture the viewport height so the inner column can grow to fill
        // it (centering the form when content fits) and grow past it
        // (engaging the scroll viewport) when the keyboard is open.
        val viewportHeight = maxHeight

        // Logout escape hatch — top-right. Otherwise the user is trapped
        // here if they accidentally landed on a wrong account.
        IconButton(
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Icon(Icons.Filled.Logout, contentDescription = "Sign out", tint = c.textPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Keep the lock icon from sliding under the top-right
                // logout button when the form scrolls up.
                Spacer(Modifier.height(48.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Set a new password",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your password was reset by your school admin. " +
                           "Please choose a new password to continue.",
                    fontSize = 13.sp,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                // No "current password" field — server (Admin SDK) does the
                // password update; no Firebase recent-login re-auth needed.
                OutlinedTextField(
                    value = uiState.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = if (uiState.newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleNewVisibility) {
                            Icon(
                                imageVector = if (uiState.newVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (uiState.newVisible) "Hide" else "Show",
                                tint = c.textSecondary,
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.glassBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = if (uiState.confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        viewModel.submit()
                    }),
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleConfirmVisibility) {
                            Icon(
                                imageVector = if (uiState.confirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (uiState.confirmVisible) "Hide" else "Show",
                                tint = c.textSecondary,
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.glassBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.errorMessage?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = msg,
                        color = c.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = !uiState.isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            color = Color_white,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "Save & Continue",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onLogout) {
                    Text("Sign out instead", fontSize = 13.sp, color = c.textSecondary)
                }
            }
        }
    }
}

private val Color_white = androidx.compose.ui.graphics.Color.White
