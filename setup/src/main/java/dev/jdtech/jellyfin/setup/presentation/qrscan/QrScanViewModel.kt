package dev.jdtech.jellyfin.setup.presentation.qrscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.backup.BackupCrypto
import dev.jdtech.jellyfin.qrsetup.QrConfigCodec
import dev.jdtech.jellyfin.qrsetup.QrConfigManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QrScanViewModel @Inject constructor(private val qrConfigManager: QrConfigManager) :
    ViewModel() {
    private val _state = MutableStateFlow(QrScanState())
    val state = _state.asStateFlow()

    private var pendingRaw: String? = null

    fun onAction(action: QrScanAction) {
        when (action) {
            is QrScanAction.OnCodeScanned -> {
                val current = _state.value
                // Camera keeps re-decoding the same still-visible code every analyzed frame -
                // ignore repeats while a decode is in flight, a password prompt is already up, or
                // we're done (about to navigate away).
                if (current.isApplying || current.needsPassword || current.done) return
                pendingRaw = action.raw
                decode(password = null)
            }
            is QrScanAction.OnPasswordSubmit -> decode(password = action.password)
            is QrScanAction.OnBackClick -> Unit
        }
    }

    private fun decode(password: String?) {
        val raw = pendingRaw ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplying = true, error = null, wrongPassword = false)
            try {
                val envelope = QrConfigCodec.decodePayload(raw, password)
                qrConfigManager.applyEnvelope(envelope)
                _state.value =
                    _state.value.copy(isApplying = false, needsPassword = false, done = true)
            } catch (e: BackupCrypto.WrongPasswordException) {
                _state.value =
                    _state.value.copy(
                        isApplying = false,
                        needsPassword = true,
                        wrongPassword = true,
                    )
            } catch (e: BackupCrypto.PasswordRequiredException) {
                _state.value = _state.value.copy(isApplying = false, needsPassword = true)
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(isApplying = false, needsPassword = false, error = e.message)
            }
        }
    }
}
