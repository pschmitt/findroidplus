package dev.pschmitt.jellyfin

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Swaps in [HiltTestApplication] so `@HiltAndroidTest`-annotated instrumented tests get a real
 * Hilt component graph. Referenced by `testInstrumentationRunner` in app/phone/build.gradle.kts. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
