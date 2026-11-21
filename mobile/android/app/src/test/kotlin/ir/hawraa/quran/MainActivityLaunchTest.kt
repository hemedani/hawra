package ir.hawraa.quran

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Collections

/**
 * Launches the real activity with the real Hilt graph, with the app language already
 * persisted as Persian, and fails if anything thrown off the main thread reaches the
 * process-wide handler — that is what closes the app a moment after it opens.
 *
 * The preference is seeded by writing the DataStore protobuf directly, because a second
 * `DataStore` instance for the same file cannot be opened in one process.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HawraaApp::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", sdk = [34])
class MainActivityLaunchTest {

    /** `PreferenceMap { preferences { key: "app_language", value { string: "fa" } } }`. */
    private fun seedPersian() {
        val context = ApplicationProvider.getApplicationContext<HawraaApp>()
        val file = File(context.filesDir, "datastore/hawraa_settings.preferences_pb")
        file.parentFile?.mkdirs()
        val key = "app_language".toByteArray()
        val value = "fa".toByteArray()
        val entry = byteArrayOf(0x0A, key.size.toByte()) + key +
            byteArrayOf(0x12, 0x04, 0x2A, value.size.toByte()) + value
        file.writeBytes(byteArrayOf(0x0A, entry.size.toByte()) + entry)
    }

    @Test
    fun startup_work_does_not_crash_the_process() {
        seedPersian()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> errors.add(error) }
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            shadowOf(Looper.getMainLooper()).idle()
            // The app-scope coroutines run on Dispatchers.IO; give them time to fail.
            repeat(40) {
                Thread.sleep(25)
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertTrue(
                "Startup work threw:\n" + errors.joinToString("\n\n") { it.stackTraceToString() },
                errors.isEmpty(),
            )
            controller.pause().stop().destroy()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
