package dev.jdtech.jellyfin.presentation.setup.qrscan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.qrsetup.QrCodec
import dev.jdtech.jellyfin.qrsetup.QrConfigCodec
import dev.jdtech.jellyfin.setup.presentation.qrscan.QrScanAction
import dev.jdtech.jellyfin.setup.presentation.qrscan.QrScanState
import dev.jdtech.jellyfin.setup.presentation.qrscan.QrScanViewModel
import dev.jdtech.jellyfin.utils.restartProcess
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

@Composable
fun QrScanScreen(
    onBackClick: () -> Unit,
    initialRaw: String? = null,
    viewModel: QrScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Arrived via the findroidplus:// deep link (e.g. tapped from a different scanner app) -
    // the payload's already in hand, so apply it straight away instead of opening the camera.
    LaunchedEffect(initialRaw) {
        initialRaw?.let { viewModel.onAction(QrScanAction.OnCodeScanned(it)) }
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            // Brief pause so the success message is actually visible before the process
            // restart (required so the newly-current server/user take effect - see
            // Activity.restartProcess()'s doc) tears everything down.
            delay(1200)
            (context as Activity).restartProcess()
        }
    }

    QrScanScreenLayout(
        state = state,
        skipCamera = initialRaw != null,
        onAction = { action ->
            when (action) {
                is QrScanAction.OnBackClick -> onBackClick()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun QrScanScreenLayout(
    state: QrScanState,
    skipCamera: Boolean,
    onAction: (QrScanAction) -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                permissionPermanentlyDenied =
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        context as Activity,
                        Manifest.permission.CAMERA,
                    )
            }
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !skipCamera)
            permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    RootLayout {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                state.done -> {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = stringResource(CoreR.string.qr_scan_success),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                skipCamera || state.isApplying -> CircularProgressIndicator()
                hasCameraPermission -> {
                    Text(
                        text = stringResource(CoreR.string.qr_scan_summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    QrCameraPreview(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        onCodeScanned = { onAction(QrScanAction.OnCodeScanned(it)) },
                    )
                }
                permissionPermanentlyDenied -> {
                    Text(text = stringResource(CoreR.string.qr_scan_permission_denied))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                            )
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(CoreR.string.qr_scan_open_settings))
                    }
                }
                else -> {
                    Text(text = stringResource(CoreR.string.qr_scan_permission_rationale))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(CoreR.string.qr_scan_title))
                    }
                }
            }

            val error = state.error
            if (error != null) {
                Text(
                    text = stringResource(CoreR.string.qr_scan_error, error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (state.needsPassword) {
        var password by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onAction(QrScanAction.OnBackClick) },
            title = { Text(text = stringResource(CoreR.string.qr_scan_password_title)) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(text = stringResource(CoreR.string.password)) },
                    isError = state.wrongPassword,
                    supportingText = {
                        if (state.wrongPassword) {
                            Text(text = stringResource(CoreR.string.qr_scan_wrong_password))
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(QrScanAction.OnPasswordSubmit(password)) }) {
                    Text(text = stringResource(CoreR.string.restore_backup_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(QrScanAction.OnBackClick) }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun QrCameraPreview(modifier: Modifier, onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val listenableFuture = ProcessCameraProvider.getInstance(ctx)
            listenableFuture.addListener(
                {
                    val cameraProvider = listenableFuture.get()

                    val preview =
                        Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        decodeQrCode(imageProxy)?.let(onCodeScanned)
                        imageProxy.close()
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                },
                ContextCompat.getMainExecutor(ctx),
            )

            previewView
        },
    )
}

private fun decodeQrCode(imageProxy: ImageProxy): String? {
    val yPlane = imageProxy.planes[0]
    val buffer = yPlane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val source =
        PlanarYUVLuminanceSource(
            data,
            yPlane.rowStride,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false,
        )
    val text = QrCodec.decode(BinaryBitmap(HybridBinarizer(source))) ?: return null
    // Ignore any other kind of QR code in view (wifi, URLs, ...) instead of flashing an
    // "invalid code" error at the user for pointing the camera at the wrong thing.
    return text.takeIf { QrConfigCodec.looksLikeQrConfigUri(it) }
}
