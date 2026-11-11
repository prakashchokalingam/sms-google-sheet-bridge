package dev.hark.smsbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hark.smsbridge.core.IstTime

@Composable
fun MessagesScreen(state: UiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            InfoBanner(
                text = "Messages stay on this phone until a batch uploads. Masked bodies are stored, not the raw code."
            )
        }

        if (state.messages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Email,
                    title = "No messages yet",
                    message = "New SMS will appear here and upload at the next window."
                )
            }
        } else {
            items(state.messages, key = { it.id }) { message ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.senderName.ifBlank { message.sender },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (message.senderName.isNotBlank()) {
                                Text(
                                    text = message.sender,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = IstTime.human(message.receivedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        SelectionContainer {
                            Text(
                                text = message.body,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (message.masked) {
                            Pill("MASKED · ${message.redactions}", Tone.GOOD)
                        }
                        if (message.batchSeq == null) {
                            Pill("Queued", Tone.NEUTRAL)
                        } else {
                            Pill("Batch #${message.batchSeq}", Tone.NEUTRAL)
                        }
                        if (message.sim >= 0) {
                            Pill("SIM ${message.sim}", Tone.NEUTRAL)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "${state.messages.size} messages on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
