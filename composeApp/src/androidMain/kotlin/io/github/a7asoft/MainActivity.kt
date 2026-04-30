package io.github.a7asoft

import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.a7asoft.nfc.SystemNfcAvailability
import io.github.a7asoft.nfcemv.reader.ContactlessReader
import io.github.a7asoft.ui.ReaderHandle
import io.github.a7asoft.ui.ReaderViewModel

/**
 * Entry-point activity. Owns NFC reader-mode lifecycle and hosts the
 * Compose UI.
 *
 * NFC reader mode is enabled in [onResume] (reader callback delegates
 * each detected [Tag] to the ViewModel via a [ReaderHandle]) and
 * disabled in [onPause]. The activity declares
 * `android:configChanges="orientation|screenSize|keyboardHidden"` in
 * the manifest so rotation does not retoggle reader mode.
 */
public class MainActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels {
        ReaderViewModel.provideFactory(SystemNfcAvailability(applicationContext))
    }

    // why: NfcAdapter.getDefaultAdapter(Context) is deprecated since API 33;
    // use the NfcManager system service. minSdk 24 covers getSystemService(Class).
    private val nfcAdapter: NfcAdapter? by lazy {
        getSystemService(NfcManager::class.java)?.defaultAdapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNfcAvailability()
        val adapter = nfcAdapter
        if (adapter != null && adapter.isEnabled) {
            adapter.enableReaderMode(this, ::onTagDiscovered, READER_MODE_FLAGS, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun onTagDiscovered(tag: Tag) {
        viewModel.onReadRequested(ReaderHandle { ContactlessReader.fromTag(tag).read() })
    }

    private companion object {
        // why: contactless EMV cards use ISO/IEC 14443 Type A or B. Skip
        // NDEF since EMV records are not NDEF-formatted.
        const val READER_MODE_FLAGS: Int = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }
}
