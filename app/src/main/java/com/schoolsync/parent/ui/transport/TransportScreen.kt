package com.schoolsync.parent.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.schoolsync.parent.ui.theme.*

@Composable
fun TransportScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Transport",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bus Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCardElevated(16.dp)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Teal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsBus,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "School Bus",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Transport Details",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TransportInfoRow(
                    icon = Icons.Filled.Route,
                    label = "Route",
                    value = "Route information not available",
                    color = InfoBlue
                )

                HorizontalDivider(
                    color = GlassBorder.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                TransportInfoRow(
                    icon = Icons.Filled.DirectionsBus,
                    label = "Vehicle No",
                    value = "Not assigned",
                    color = WarningAmber
                )

                HorizontalDivider(
                    color = GlassBorder.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                TransportInfoRow(
                    icon = Icons.Filled.Person,
                    label = "Driver",
                    value = "Not assigned",
                    color = SuccessGreen
                )
            }

            // Timing Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(16.dp)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Timings",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimingCard(
                        label = "Pickup",
                        time = "--:--",
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    TimingCard(
                        label = "Drop",
                        time = "--:--",
                        color = InfoBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stop Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(16.dp)
                    .padding(20.dp)
            ) {
                TransportInfoRow(
                    icon = Icons.Filled.LocationOn,
                    label = "Pickup/Drop Point",
                    value = "Not assigned",
                    color = ErrorRed
                )
            }

            // Placeholder note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InfoBlueBg)
                    .padding(14.dp)
            ) {
                Text(
                    text = "Transport details will be updated by your school administration. Contact the school office for any transport-related queries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InfoBlue,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun TransportInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TimingCard(
    label: String,
    time: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}
