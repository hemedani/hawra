package ir.hawraa.quran.data.recitation

/**
 * Reads text aloud — the other half of a preset, next to the reciter's audio.
 *
 * An interface for the same reason [RecitationEngine] is one: the player must be testable on
 * the JVM, and `TextToSpeech` needs a real device. It is also the seam where a future
 * bundled-recitation translation could replace TTS without touching the player.
 *
 * [speak] **suspends until the utterance finishes**, which is what lets the preset player be
 * a plain sequential loop instead of a callback graph.
 */
interface SpeechEngine {

    /** True when the platform can speak at all — the UI hides spoken steps otherwise. */
    val available: Boolean

    /**
     * Speaks [text] and returns when it has finished (or immediately if it cannot speak).
     *
     * Cancellation stops the utterance: the player cancels the run on pause, and a voice that
     * kept talking after the user pressed pause would be the most obvious possible bug.
     */
    suspend fun speak(text: String, languageCode: String?, speed: Float)

    /** Stops whatever is being said. Safe to call when idle. */
    fun stop()

    fun release()
}
