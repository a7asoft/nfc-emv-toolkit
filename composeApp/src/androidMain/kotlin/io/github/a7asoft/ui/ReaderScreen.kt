package io.github.a7asoft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.a7asoft.ui.components.CardSummary
import io.github.a7asoft.ui.components.ErrorPanel
import io.github.a7asoft.ui.components.ReadingProgress
import io.github.a7asoft.ui.components.StatusHeader

/**
 * Pure-presentation screen for the reader flow.
 *
 * State and callbacks are hoisted; the screen renders only what the
 * caller passes. Test by passing a fake [ReaderUiState] and asserting
 * which sub-composable is shown.
 */
@Composable
public fun ReaderScreen(
    state: ReaderUiState,
    onTryAgain: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusHeader(state, onOpenNfcSettings)
            ReaderContent(state, onTryAgain)
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
// why: exhaustive `when` over the sealed `ReaderUiState` catalogue. Each
// branch is a single mapping, not an arm of real cyclomatic complexity.
private fun ReaderContent(state: ReaderUiState, onTryAgain: () -> Unit) {
    when (state) {
        ReaderUiState.NfcUnavailable, ReaderUiState.NfcDisabled, ReaderUiState.Idle -> Unit
        ReaderUiState.TagDetected -> ReadingProgress(stageLabel = "Tag detected")
        ReaderUiState.SelectingPpse -> ReadingProgress(stageLabel = "Selecting application directory")
        is ReaderUiState.SelectingApplication -> ReadingProgress(stageLabel = "Selecting application ${state.aid}")
        ReaderUiState.ReadingRecords -> ReadingProgress(stageLabel = "Reading records")
        is ReaderUiState.Done -> CardSummary(card = state.card, onTryAgain = onTryAgain)
        is ReaderUiState.Failed -> ErrorPanel(error = state.error, onTryAgain = onTryAgain)
    }
}
