package com.usatov.aura.logger.internal

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Process-wide runtime of the SDK. Own sandbox (SDK 33): the runtime is a singleton and the
 * corrupted-file test must run before anything else opens the config DataStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuraLoggerRuntimeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun errorLogged(fragment: String) =
        ShadowLog.getLogsForTag("AuraLogger").any { it.type == Log.ERROR && it.msg.contains(fragment, ignoreCase = true) }

    @Test
    fun corruptedConfigFileIsResetAndReadAsMissing() = runBlocking {
        context.preferencesDataStoreFile(AuraLoggerRuntime.settings.configFileName).apply {
            parentFile?.mkdirs()
            writeBytes("not a protobuf".toByteArray())
        }

        assertNull(AuraLoggerRuntime.configStore(context).read())
        assertTrue(errorLogged("corrupted"))
    }

    @Test
    fun uncaughtBackgroundErrorsAreLoggedNotCrashing() = runBlocking {
        AuraLoggerRuntime.scope.launch { throw IllegalStateException("background failure") }
        repeat(50) {
            if (errorLogged("background")) return@runBlocking
            delay(20)
        }
        assertTrue(errorLogged("background"))
    }
}
