package dev.pschmitt.jellyfin

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Best-effort named screenshot written straight to the app's external files dir, independent of
 * fastlane screengrab's own capture/pull mechanism (which drops all its captures once the test
 * class reports a failure). Used only as a failure-diagnostics fallback in [StoreScreenshotTest] -
 * see its catch block.
 */
internal fun captureE2eScreenshot(name: String) {
    runCatching {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val directory =
            instrumentation.targetContext
                .getExternalFilesDir("e2e-screenshots")
                ?.apply(File::mkdirs) ?: return
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        File(directory, "$safeName.png").outputStream().use { output ->
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        screenshot.recycle()
    }
}
