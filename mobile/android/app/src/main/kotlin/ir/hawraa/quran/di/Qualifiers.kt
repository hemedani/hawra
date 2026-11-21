package ir.hawraa.quran.di

import javax.inject.Qualifier

/** Application-lifetime coroutine scope: work that must outlive a ViewModel. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The main (UI) thread dispatcher.
 *
 * ExoPlayer is single-threaded by contract: every call must run on the thread that created it,
 * which is the main thread. A suspending run loop that lives on [ApplicationScope] (IO) therefore
 * has to hop here before touching the player — otherwise Media3 throws
 * `IllegalStateException: Player accessed on the wrong thread` and, because the scope has no
 * exception handler, the process dies. Injected rather than `Dispatchers.Main` inline so the loop
 * stays unit-testable on the JVM with a test dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Main

/**
 * Root directory for downloaded recitation audio.
 *
 * Qualified rather than a bare `File` so it cannot be confused with the Media3 stream cache —
 * one is a cache the system may evict, the other is the user's deliberate download.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AudioRoot

/**
 * The backend's origin — scheme, host and port, no path.
 *
 * Qualified rather than a bare `String` because a `String` injection point is otherwise a
 * coin flip: this module already deals in edition keys, reciter keys and URLs, and a server
 * origin that silently arrived in the wrong parameter would fail as a 404 on a file rather
 * than as a build error.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerUrl
