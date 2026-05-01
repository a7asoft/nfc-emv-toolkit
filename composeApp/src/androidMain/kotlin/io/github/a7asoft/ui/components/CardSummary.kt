package io.github.a7asoft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.a7asoft.nfcemv.extract.EmvCard

/**
 * Renders an [EmvCard] using only its safe accessors:
 * - `pan.toString()` (always masked per [io.github.a7asoft.nfcemv.extract.Pan]).
 * - `brand.displayName` (human-readable label per
 *   [io.github.a7asoft.nfcemv.brand.EmvBrand]).
 * - `expiry`, `cardholderName?`, `applicationLabel?`, `aid` — non-PCI
 *   operational metadata.
 *
 * NEVER calls `pan.unmasked()` — sample app explicitly does not show
 * raw PAN even with the card in hand.
 */
@Composable
public fun CardSummary(card: EmvCard, onTryAgain: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field(label = "PAN", value = card.pan.toString())
            Field(label = "Brand", value = card.brand.displayName)
            Field(label = "Expiry", value = card.expiry.toString())
            Field(label = "Cardholder", value = card.cardholderName ?: "<not provided>")
            Field(label = "Label", value = card.applicationLabel ?: "<not provided>")
            Field(label = "AID", value = card.aid.toString())
            OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Read another card")
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
