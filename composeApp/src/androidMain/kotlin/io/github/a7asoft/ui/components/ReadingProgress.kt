package io.github.a7asoft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Linear-progress + stage-label pair shown during the read flow. The
 * caller passes a human-readable [stageLabel] derived from the current
 * `ReaderUiState`.
 */
@Composable
public fun ReadingProgress(stageLabel: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(text = stageLabel, style = MaterialTheme.typography.bodyMedium)
    }
}
