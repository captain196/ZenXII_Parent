package com.schoolsync.parent.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.parent.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class SchoolContact(
    val name: String,
    val principal: String,
    val phone: String,
    val email: String,
    val address: String,
)

private sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Success(val contact: SchoolContact) : LookupState
    data class Failure(val message: String) : LookupState
}

/**
 * "Forgot password?" dialog shown from the login screen. The user enters
 * their school code; we look up the school's admin contact info from
 * Firestore `schools/{code}` and display it. Recovery itself is
 * out-of-band — the parent calls/emails the admin who triggers a reset
 * from the admin panel (which sets the `must_change_password` claim).
 *
 * Firestore rule allows unauthenticated reads of the schools collection
 * for this lookup; the dialog displays only contact-safe fields.
 */
@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var schoolCode by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<LookupState>(LookupState.Idle) }

    fun lookup() {
        val code = schoolCode.trim()
        if (code.isEmpty()) {
            state = LookupState.Failure("Enter your school code first.")
            return
        }
        state = LookupState.Loading
        scope.launch {
            state = fetchSchoolContact(code)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Forgot password?",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Passwords can only be reset by your school's admin. " +
                        "Enter your school code below to see who to contact.",
                    style = TextStyle(fontSize = 13.sp, color = c.textSecondary, lineHeight = 18.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = schoolCode,
                    onValueChange = {
                        schoolCode = it
                        if (state is LookupState.Failure || state is LookupState.Success) {
                            state = LookupState.Idle
                        }
                    },
                    label = { Text("School code", style = TextStyle(fontSize = 13.sp)) },
                    placeholder = { Text("e.g. 10005", style = TextStyle(fontSize = 13.sp)) },
                    singleLine = true,
                    enabled = state !is LookupState.Loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { lookup() }),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                when (val s = state) {
                    is LookupState.Idle -> Unit
                    is LookupState.Loading -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = c.accent
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                "Looking up your school…",
                                style = TextStyle(fontSize = 12.sp, color = c.textSecondary)
                            )
                        }
                    }
                    is LookupState.Failure -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = s.message,
                            style = TextStyle(fontSize = 12.sp, color = c.error)
                        )
                    }
                    is LookupState.Success -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        ContactCard(s.contact)
                    }
                }
            }
        },
        confirmButton = {
            if (state is LookupState.Success) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                Button(
                    onClick = { lookup() },
                    enabled = state !is LookupState.Loading
                ) { Text("Look up") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ContactCard(contact: SchoolContact) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.School,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = contact.name.ifBlank { "Your school" },
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
            )
        }
        if (contact.principal.isNotBlank()) {
            ContactRow(Icons.Filled.Person, "Principal", contact.principal)
        }
        if (contact.phone.isNotBlank()) {
            ContactRow(Icons.Filled.Phone, "Phone", contact.phone)
        }
        if (contact.email.isNotBlank()) {
            ContactRow(Icons.Filled.Email, "Email", contact.email)
        }
        if (contact.address.isNotBlank()) {
            ContactRow(Icons.Filled.LocationOn, "Address", contact.address)
        }
    }
}

@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = label,
            tint = c.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(8.dp))
        Column {
            Text(
                text = label,
                style = TextStyle(fontSize = 11.sp, color = c.textTertiary)
            )
            Text(
                text = value,
                style = TextStyle(fontSize = 13.sp, color = c.textPrimary, lineHeight = 18.sp)
            )
        }
    }
}

private suspend fun fetchSchoolContact(code: String): LookupState {
    val firestore = FirebaseFirestore.getInstance()
    return try {
        // 1) Direct doc lookup — works when docId matches the entered code
        //    (legacy numeric schoolIds like "10005" and SCH_* ids alike).
        val direct = firestore.collection("schools").document(code).get().await()
        val data = if (direct.exists()) direct.data else null

        // 2) Fall back to schoolCode-field query for new schools where the
        //    docId is SCH_XXXXXX but the user only knows their 5-digit code.
        val resolved = data ?: run {
            val q = firestore.collection("schools")
                .whereEqualTo("schoolCode", code)
                .limit(1)
                .get()
                .await()
            q.documents.firstOrNull()?.data
        }

        if (resolved == null) {
            LookupState.Failure(
                "No school found for code \"$code\". Check the code and try again."
            )
        } else {
            LookupState.Success(
                SchoolContact(
                    name = (resolved["name"] as? String).orEmpty(),
                    principal = (resolved["principal"] as? String).orEmpty(),
                    phone = (resolved["phone"] as? String).orEmpty(),
                    email = (resolved["email"] as? String).orEmpty(),
                    address = listOfNotNull(
                        resolved["address"] as? String,
                        resolved["city"] as? String,
                        resolved["state"] as? String,
                    ).filter { it.isNotBlank() }.joinToString(", "),
                )
            )
        }
    } catch (e: Exception) {
        LookupState.Failure(
            "Couldn't reach the server. Check your connection and try again."
        )
    }
}
