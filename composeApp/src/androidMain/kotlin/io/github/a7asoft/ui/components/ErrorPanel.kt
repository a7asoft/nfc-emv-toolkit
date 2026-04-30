package io.github.a7asoft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.a7asoft.nfcemv.reader.IoReason
import io.github.a7asoft.nfcemv.reader.ReaderError

/**
 * Renders a [ReaderError] as a friendly headline plus a collapsible
 * diagnostic detail (`error.toString()`) and a "Try again" CTA.
 *
 * `error.toString()` is safe to display: every [ReaderError] variant
 * carries only structural metadata (status words, structural sub-error
 * references, [IoReason] enum) — never raw card bytes or arbitrary
 * exception messages.
 */
@Composable
public fun ErrorPanel(error: ReaderError, onTryAgain: () -> Unit) {
    val expandedState = remember { mutableStateOf(false) }
    val expanded = expandedState.value
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = friendlyMessage(error), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { expandedState.value = !expandedState.value }) {
                Text(if (expanded) "Hide details" else "Show details")
            }
            if (expanded) {
                Text(text = error.toString(), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Try again")
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
// why: exhaustive when over sealed [ReaderError] catalogue. Each branch is
// a single mapping, not real complexity.
private fun friendlyMessage(error: ReaderError): String = when (error) {
    is ReaderError.IoFailure -> friendlyIoMessage(error.reason)
    ReaderError.PpseUnsupported -> "This card does not advertise a contactless EMV directory."
    ReaderError.NoApplicationSelected -> "The card listed no contactless applications."
    is ReaderError.ApduStatusError -> "The card returned an unexpected status."
    is ReaderError.PpseRejected -> "The PPSE response could not be parsed."
    is ReaderError.GpoRejected -> "The GET PROCESSING OPTIONS response could not be parsed."
    is ReaderError.ParseFailed -> "The card data could not be parsed into an EMV card."
}

@Suppress("CyclomaticComplexMethod")
// why: 3-branch sealed dispatch over [IoReason]; detekt counts the
// function itself as +1, tipping the threshold. The suppression matches
// that algorithm.
private fun friendlyIoMessage(reason: IoReason): String = when (reason) {
    IoReason.TagLost -> "The card was moved away too quickly. Hold it steady."
    IoReason.Timeout -> "The card took too long to respond. Try again."
    IoReason.Generic -> "Communication with the card failed."
}
