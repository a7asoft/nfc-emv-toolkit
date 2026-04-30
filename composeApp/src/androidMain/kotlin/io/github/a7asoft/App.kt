package io.github.a7asoft

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.a7asoft.ui.ReaderScreen
import io.github.a7asoft.ui.ReaderViewModel

/**
 * Top-level composable that wires [ReaderViewModel.state] into
 * [ReaderScreen]. Hoisted callbacks (`onTryAgain`, `onOpenNfcSettings`)
 * keep the screen pure-presentation and unit-testable in isolation.
 */
@Composable
public fun App(viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MaterialTheme {
        ReaderScreen(
            state = state,
            onTryAgain = viewModel::reset,
            onOpenNfcSettings = {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            },
        )
    }
}
