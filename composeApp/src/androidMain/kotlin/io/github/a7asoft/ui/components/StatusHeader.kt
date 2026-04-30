package io.github.a7asoft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.a7asoft.ui.ReaderUiState

/**
 * Top region of the reader screen. Shows a state-driven headline and
 * subtitle, plus an "Open NFC settings" CTA when NFC is disabled.
 */
@Composable
public fun StatusHeader(
    state: ReaderUiState,
    onOpenNfcSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = headlineFor(state),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitleFor(state),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (state is ReaderUiState.NfcDisabled) {
            Button(onClick = onOpenNfcSettings) { Text("Open NFC settings") }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
// why: exhaustive when over sealed UI state; intermediate progress states
// collapse via `else ->`. Each branch is a single mapping.
private fun headlineFor(state: ReaderUiState): String = when (state) {
    ReaderUiState.NfcUnavailable -> "NFC not supported"
    ReaderUiState.NfcDisabled -> "NFC is off"
    ReaderUiState.Idle -> "Tap a card"
    is ReaderUiState.Done -> "Card read"
    is ReaderUiState.Failed -> "Read failed"
    else -> "Reading"
}

@Suppress("CyclomaticComplexMethod")
// why: exhaustive when over sealed UI state; intermediate progress states
// collapse via `else ->`. Each branch is a single mapping.
private fun subtitleFor(state: ReaderUiState): String = when (state) {
    ReaderUiState.NfcUnavailable -> "This device has no NFC hardware. The reader cannot run here."
    ReaderUiState.NfcDisabled -> "Enable NFC in system settings to read a card."
    ReaderUiState.Idle -> "Hold a contactless EMV card against the back of the device."
    is ReaderUiState.Done -> "Hold another card to read again."
    is ReaderUiState.Failed -> "Try again, or use a different card."
    else -> "Hold the card steady"
}
