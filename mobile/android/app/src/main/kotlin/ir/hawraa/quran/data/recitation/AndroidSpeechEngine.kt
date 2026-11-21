package ir.hawraa.quran.data.recitation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android's `TextToSpeech`, wrapped so the preset player can treat "read this translation
 * aloud" as one suspending call.
 *
 * Three things make this less trivial than it looks:
 *
 *  - **Initialisation is asynchronous**, and `speak` called before it completes is silently
 *    dropped by the platform. So the first call waits for init, and a device with no Persian
 *    voice is reported as unavailable instead of speaking English at a Persian text.
 *  - **Utterance ids must be unique**, or a stale `onDone` can resume the wrong call. They are
 *    counter-based, and a completion only resumes the coroutine that owns that id.
 *  - **`TextToSpeech` must be created and shut down on a thread with a looper**, so it is
 *    created lazily on the main thread and released with the application.
 */
@Singleton
class AndroidSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechEngine {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false

    /** Guards one-time initialisation; `speak` may be called from several places. */
    private val initMutex = Mutex()

    /** Utterance ids, and the callback waiting on each. */
    private val ids = AtomicInteger(0)
    private val pending = mutableMapOf<String, () -> Unit>()

    override val available: Boolean get() = ready

    override suspend fun speak(text: String, languageCode: String?, speed: Float) {
        if (text.isBlank()) return
        ensureInitialised()
        if (!ready) return

        val engine = tts ?: return
        val id = "hawraa-${ids.incrementAndGet()}"
        withContext(Dispatchers.Main) {
            engine.setSpeechRate(speed.coerceIn(MIN_RATE, MAX_RATE))
            languageCode?.let { tag ->
                Locale.forLanguageTag(tag).takeIf { it.language.isNotBlank() }?.let(engine::setLanguage)
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        awaitUtterance(id)
    }

    /** Suspends until [id] reports done, error, or the caller is cancelled. */
    private suspend fun awaitUtterance(id: String) = suspendCancellableCoroutine { continuation ->
        synchronized(pending) {
            pending[id] = {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        continuation.invokeOnCancellation {
            // Cancelling the run must silence the voice, not just abandon the wait.
            tts?.stop()
            synchronized(pending) { pending.remove(id) }
        }
    }

    private suspend fun ensureInitialised() = initMutex.withLock {
        if (ready || failed) return@withLock
        withContext(Dispatchers.Main) {
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                failed = status != TextToSpeech.SUCCESS
                if (ready) {
                    @Suppress("OVERRIDE_DEPRECATION") // the no-arg onError is still called by older engines
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = complete(utteranceId)
                        override fun onError(utteranceId: String?) = complete(utteranceId)
                        override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId)
                    })
                }
            }
        }
    }

    private fun complete(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = synchronized(pending) { pending.remove(utteranceId) }
        callback?.invoke()
    }

    override fun stop() {
        tts?.stop()
        val waiting = synchronized(pending) {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        waiting.forEach { it() }
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        /** `setSpeechRate` is relative to normal; outside this it is unintelligible. */
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
    }
}
