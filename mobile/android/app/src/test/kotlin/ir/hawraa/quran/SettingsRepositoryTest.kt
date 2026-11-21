package ir.hawraa.quran

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.repository.SettingsRepository
import ir.hawraa.quran.domain.AppLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The language preference round-trips through DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryTest {

    @Test
    fun app_language_round_trips() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) {
            File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb")
        }
        val repository = SettingsRepository(dataStore)

        assertEquals(AppLanguage.SYSTEM, repository.appLanguage.first())

        repository.setAppLanguage(AppLanguage.PERSIAN)
        assertEquals(AppLanguage.PERSIAN, repository.appLanguage.first())

        repository.setAppLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, repository.appLanguage.first())
    }
}
