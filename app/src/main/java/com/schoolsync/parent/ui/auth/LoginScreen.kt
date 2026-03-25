package com.schoolsync.parent.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
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
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val c = LocalAppColors.current

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onLoginSuccess()
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
            Spacer(modifier = Modifier.height(72.dp))

            // ── Logo ──
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(c.accent, c.accentSecondary))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = "SchoolSync",
                    tint = if (c.isDark) c.bgStart else Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Brand ──
            Text(
                text = "SchoolSync",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    color = c.textPrimary
                )
            )
            Text(
                text = "Parent Portal",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.accent,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Login Card ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(20.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome Back",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sign in to continue",
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
                    label = { Text("Student ID", style = TextStyle(fontSize = 13.sp)) },
                    placeholder = { Text("Enter your Student ID", style = TextStyle(fontSize = 13.sp)) },
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
                    label = { Text("Password", style = TextStyle(fontSize = 13.sp)) },
                    placeholder = { Text("Enter your password", style = TextStyle(fontSize = 13.sp)) },
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
                            text = "Sign In",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Contact your school administrator\nif you forgot your credentials",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            )

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
