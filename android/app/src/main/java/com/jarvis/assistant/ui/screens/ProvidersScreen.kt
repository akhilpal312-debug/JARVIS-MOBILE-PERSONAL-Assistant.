package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.EmptyHint
import com.jarvis.assistant.ui.InfoCard
import com.jarvis.assistant.ui.ScreenScaffold
import com.jarvis.assistant.ui.StatusPill
import com.jarvis.assistant.ui.UiState

@Composable
fun ProvidersScreen(state: UiState, onMenu: () -> Unit, onRefresh: () -> Unit) {
    ScreenScaffold(
        title = "AI models",
        subtitle = "Provider status for your backend",
        onMenu = onMenu,
        actions = {
            if (state.providerLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh provider status")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.backendConfigured) {
                item {
                    InfoCard(
                        text = "Add your backend URL and deployment token in Settings to see live provider status. " +
                            "Provider API keys never reach the phone — they live only in Vercel environment variables.",
                        icon = Icons.Outlined.Cloud,
                    )
                }
            }

            state.providerError?.let { error ->
                item { EmptyHint(error) }
            }

            val status = state.providerStatus
            if (status != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Backend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                StatusPill("Online", MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Cloud sync storage: ${status.storage}. " +
                                    if (status.storage == "memory") {
                                        "Configure Supabase for durable multi-device sync."
                                    } else {
                                        "Tasks and memories sync durably."
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (status != null) {
                items(status.providers.size) { index ->
                    val provider = status.providers[index]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    provider.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusPill(
                                    if (provider.configured) "Active" else "No key",
                                    if (provider.configured) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            if (provider.models.isNotEmpty()) {
                                Text(
                                    provider.models.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(provider.freeTier, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                provider.rateLimit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Free-tier terms are shown as published by each provider and can change. " +
                        "Jarvis falls back across configured providers and reports honestly when none is available.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
