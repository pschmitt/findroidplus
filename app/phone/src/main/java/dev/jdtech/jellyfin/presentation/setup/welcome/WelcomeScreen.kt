package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.welcome.WelcomeAction

@Composable
fun WelcomeScreen(
    onContinueClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onScanQrClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    WelcomeScreenLayout(
        onAction = { action ->
            when (action) {
                is WelcomeAction.OnContinueClick -> onContinueClick()
                is WelcomeAction.OnRestoreClick -> onRestoreClick()
                is WelcomeAction.OnScanQrClick -> onScanQrClick()
                is WelcomeAction.OnLearnMoreClick -> {
                    uriHandler.openUri("https://jellyfin.org/")
                }
            }
        }
    )
}

@Composable
private fun WelcomeScreenLayout(onAction: (WelcomeAction) -> Unit) {
    RootLayout(padding = PaddingValues(horizontal = 24.dp)) {
        // Kept out of the main button stack and pinned to the corner since it's an informational
        // link, not part of the setup flow's call to action.
        TextButton(
            onClick = { onAction(WelcomeAction.OnLearnMoreClick) },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.height(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(SetupR.string.welcome_btn_learn_more))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
        ) {
            Image(
                painter = painterResource(id = CoreR.drawable.ic_banner),
                contentDescription = null,
                modifier = Modifier.width(250.dp),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(SetupR.string.welcome),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(SetupR.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Column(modifier = Modifier.widthIn(max = 480.dp)) {
                // Primary CTA: taller and with larger type than the secondary actions below so it
                // reads as the obvious next step.
                Button(
                    onClick = { onAction(WelcomeAction.OnContinueClick) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        text = stringResource(SetupR.string.welcome_btn_continue),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_arrow_right),
                        contentDescription = null,
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                OutlinedButton(
                    onClick = { onAction(WelcomeAction.OnScanQrClick) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_qr_code),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(SetupR.string.welcome_btn_scan_qr))
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                OutlinedButton(
                    onClick = { onAction(WelcomeAction.OnRestoreClick) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(SetupR.string.welcome_btn_restore))
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun WelcomeScreenLayoutPreview() {
    FindroidTheme { WelcomeScreenLayout(onAction = {}) }
}
