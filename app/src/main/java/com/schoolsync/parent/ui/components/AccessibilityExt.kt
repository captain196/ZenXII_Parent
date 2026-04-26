package com.schoolsync.parent.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Apply a content description to a custom composable that talkback would
 * otherwise see as a soup of children. Merges descendants so the screen
 * reader announces only this label.
 */
fun Modifier.describeAs(label: String, role: Role? = Role.Button): Modifier =
    this.semantics(mergeDescendants = true) {
        contentDescription = label
        if (role != null) this.role = role
    }
