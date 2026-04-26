package com.schoolsync.parent.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schoolsync.parent.data.local.DevPrefs
import kotlinx.coroutines.launch

/**
 * Hidden dev-only dialog for overriding the PHP backend BASE_URL at
 * runtime. Triggered by long-pressing the app title on the Login screen.
 *
 * Use cases:
 *  - PC's LAN IP changed mid-test session → paste the new
 *    `http://10.x.x.x/Grader/school/` URL, save, no rebuild needed.
 *  - Pointing the app at an ngrok / Cloudflare tunnel for off-network
 *    testing.
 *  - Quickly diffing prod vs staging without two builds.
 *
 * The override persists in DataStore (separate from auth state, so
 * sign-out doesn't wipe it). A "Reset" button restores the
 * compile-time default.
 */
@Composable
fun DevSettingsDialog(
    devPrefs: DevPrefs,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val current by devPrefs.effectiveBaseUrl.collectAsState(initial = devPrefs.defaultBaseUrl())
    var input by remember { mutableStateOf("") }

    // Pre-fill the field with the active URL on first open so the user
    // can edit it in place rather than retyping the whole thing.
    LaunchedEffect(current) {
        if (input.isEmpty()) input = current
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dev Settings") },
        text = {
            Column {
                Text(
                    "Override the PHP backend URL for testing. Take effect immediately, no rebuild needed.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("http://10.31.184.16/Grader/school/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Compile default: ${devPrefs.defaultBaseUrl()}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
                Text(
                    "Currently active: $current",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    devPrefs.setOverride(input)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = {
                    scope.launch {
                        devPrefs.clearOverride()
                        onDismiss()
                    }
                }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
