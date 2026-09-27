package com.schoolsync.parent.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.sync.StudentProfileSynchronizer
import com.schoolsync.parent.ui.theme.LocalAppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  A WITHDRAWAL MUST END AN OPEN SESSION, NOT ONLY THE NEXT LOGIN
 * ══════════════════════════════════════════════════════════════════════════
 *
 * `AuthRepository.login()` already refuses to complete when the student's
 * status is anything other than "Active" — a withdrawn student's parent
 * cannot sign in on stale credentials. But that check ran at LOGIN only, so a
 * session already open when the withdrawal happened kept working.
 *
 * [StudentProfileSynchronizer] now carries the live status into the cached
 * profile and raises `profileRevoked`. This is the consumer that acts on it,
 * so the flag is not another listener with no callers.
 *
 * ── WHY A SCREEN AND NOT A FORCED LOGOUT ─────────────────────────────────
 * This mirrors the school-level gate, which shows `SchoolUnavailableScreen`
 * rather than silently bouncing the parent to Login. The reasoning carries
 * over: a parent thrown to a login screen with no explanation reads it as the
 * app breaking, and would simply sign in again — which login then refuses,
 * with no more explanation than before. Telling them what changed, and
 * leaving the sign-out as their action, is both kinder and clearer.
 *
 * The synchroniser deliberately does not do this itself: forcing a sign-out
 * from a process-lifetime singleton would take the decision away from the UI.
 */
@HiltViewModel
class StudentAccessViewModel @Inject constructor(
    synchronizer: StudentProfileSynchronizer,
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** The live status once it is one login would refuse, else null. */
    val revokedStatus: StateFlow<String?> = synchronizer.revokedStatus

    /**
     * Ends the session through the SAME path the Profile screen uses, so a
     * revoked session is torn down exactly like a voluntary sign-out — one
     * logout implementation, not two that can drift.
     *
     * The result is not inspected because there is nothing to inspect:
     * `AuthRepository.logout()` clears the local session on both its branches
     * and returns Success either way. What matters here is that the local
     * session is gone before [onDone] navigates.
     */
    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}

/**
 * Completion and departure are different events and must not share a sentence.
 * The admin panel graduates students at the school's own terminal class, so a
 * family finishing Class 10 reaches this screen by the ordinary happy path —
 * telling them the child "is no longer enrolled" would be a poor way to mark
 * the end of eleven years at the school.
 *
 * The status itself is never shown. "TC", "Inactive" and "Alumni" are the
 * admin panel's vocabulary, and the reason a particular child left is between
 * the school and the family.
 */
@Composable
fun StudentAccessRevokedScreen(status: String?, onSignOut: () -> Unit) {
    val colors = LocalAppColors.current
    val completed = status.equals("Alumni", ignoreCase = true)
    val title = if (completed) "Schooling complete" else "This student is no longer enrolled"
    val body = if (completed) {
        "Congratulations — this student has completed their schooling. " +
            "Records for the final session stay with the school, so please contact " +
            "the school office if you need any documents."
    } else {
        "The school has updated this student's enrolment, so the app no longer " +
            "has access to their records. Please contact the school office if " +
            "you think this is a mistake."
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(colors.bgStart, colors.bgMid, colors.bgEnd)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = body,
                color = colors.textSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onSignOut) {
                Text(text = "Sign out", color = colors.textPrimary, fontSize = 15.sp)
            }
        }
    }
}
