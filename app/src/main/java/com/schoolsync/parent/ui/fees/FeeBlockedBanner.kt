package com.schoolsync.parent.ui.fees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.schoolsync.parent.ui.theme.LocalAppColors

/**
 * Reused across Dashboard + Results + (future) TC screen — surfaces a
 * pay-your-dues warning when the student has outstanding fees that may
 * trigger withholding of results / TC / hall-ticket.
 *
 * The actual blocking decision is made server-side via
 * Fee_dues_check::check(). This banner is the *client-side*
 * pre-emptive nudge: "you have dues, here's the consequence, tap
 * to pay" — so parents understand WHY the next screen might say
 * "result withheld".
 *
 * @param dueAmount   total outstanding fees in INR (≤ 0 → banner hidden)
 * @param scope       human-readable consequence — e.g. "Results may be
 *                    withheld" / "TC cannot be issued" / generic
 * @param onPayClick  fires when the parent taps the banner's CTA
 */
@Composable
fun FeeBlockedBanner(
    dueAmount: Double,
    scope: String = "Results, TC and hall-ticket may be withheld",
    onPayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (dueAmount <= 0.0) return
    val c = LocalAppColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.error.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = c.error.copy(alpha = 0.30f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onPayClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.error.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = c.error,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Outstanding fees: Rs. ${"%,.0f".format(dueAmount)}",
                style = MaterialTheme.typography.titleSmall,
                color = c.error,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = scope,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Pay now",
            tint = c.error,
            modifier = Modifier.size(18.dp)
        )
    }
}
