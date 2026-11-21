package ir.hawraa.quran.data.preset

import ir.hawraa.quran.domain.preset.AyahProgress
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetEvent
import ir.hawraa.quran.domain.preset.PresetEventKind
import ir.hawraa.quran.domain.preset.PresetSitting
import ir.hawraa.quran.domain.preset.PresetSittingStatus
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.preset.PresetStepKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire shapes for the preset domain, mirrored field-for-field from
 * `back/src/presets/presetInput.ts` and friends.
 *
 * These DTOs do double duty: they are the Lesan payload **and** the JSON stored in Room's
 * `stepsJson` / `eventsJson` columns. One shape for both is deliberate — a step that
 * round-trips through the server unchanged but through local storage lossily would be a
 * difference nobody could debug, and it would appear as "my preset changed on restart".
 */
@Serializable
data class PresetStepDto(
    val kind: String,
    val textKey: String? = null,
    val repeat: Int = 1,
    val speed: Float? = null,
    val gapMs: Int = 0,
    val languageCode: String? = null,
)

@Serializable
data class PresetEventDto(
    val kind: String,
    val at: Long,
    val surah: Int,
    val ayah: Int,
    val stepIndex: Int = 0,
)

@Serializable
data class PresetDto(
    val clientId: String,
    val name: String,
    val description: String? = null,
    val steps: List<PresetStepDto> = emptyList(),
    val reciterKey: String? = null,
    val autoAdvance: Boolean = true,
    val ayahGapMs: Int = 0,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    /** Server-side version; ignored when sending, adopted when receiving. */
    val revision: Int? = null,
    val updatedAtMs: Long = 0L,
    /** Server id, only ever present in a response. */
    val _id: String? = null,
)

/** One preset row inside a `syncPresets` request. */
@Serializable
data class PresetSyncRequest(val presets: List<PresetDto>)

@Serializable
data class PresetSyncResponse(
    val synced: Int = 0,
    val received: Int = 0,
    /** The rows the server actually kept — the client adopts these verbatim. */
    val presets: List<PresetDto> = emptyList(),
)

@Serializable
data class PresetSittingDto(
    val clientId: String,
    val presetClientId: String? = null,
    val presetName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val activeSec: Int = 0,
    val fromSurah: Int,
    val fromAyah: Int,
    val toSurah: Int,
    val toAyah: Int,
    val fromPage: Int? = null,
    val toPage: Int? = null,
    val ayahsCompleted: Int = 0,
    val stepsCompleted: Int = 0,
    val playCount: Int = 0,
    val pauseCount: Int = 0,
    val seekCount: Int = 0,
    val lastEventAt: Long = 0L,
    val status: String = "finalized",
    val reciterKey: String? = null,
    val speed: Float? = null,
    val events: List<PresetEventDto> = emptyList(),
)

@Serializable
data class ProgressEntryDto(
    val surah: Int,
    val ayah: Int,
    val globalAyah: Int,
    val page: Int? = null,
    val plays: Int = 0,
    val listenedSec: Int = 0,
    val firstAt: Long,
    val lastAt: Long,
    val lastKind: String? = null,
)

@Serializable
data class SyncProgressRequest(
    val presetClientId: String,
    val entries: List<ProgressEntryDto>,
)

@Serializable
data class SyncProgressResponse(
    val presetClientId: String? = null,
    val received: Int = 0,
    val inserted: Int = 0,
    val merged: Int = 0,
)

/** A preset id the server acknowledged. */
@Serializable
data class PresetSyncMapping(val clientId: String, val serverId: String? = null)

/**
 * Conversion in both directions, tolerant on the way in.
 *
 * `fromWire` is where a preset from a newer server or an older build stops being able to
 * hurt anything: unknown step kinds are dropped rather than guessed at, and the result is
 * run through [Preset.sanitized] so the player never receives a step it cannot play.
 */
object PresetCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun toDto(preset: Preset): PresetDto = PresetDto(
        clientId = preset.clientId,
        name = preset.name,
        description = preset.description,
        steps = preset.steps.map(::toDto),
        reciterKey = preset.reciterKey,
        autoAdvance = preset.autoAdvance,
        ayahGapMs = preset.ayahGapMs,
        isDefault = preset.isDefault,
        isArchived = preset.isArchived,
        updatedAtMs = preset.updatedAtMs,
    )

    fun toDto(step: PresetStep): PresetStepDto = PresetStepDto(
        kind = step.kind.wire,
        textKey = step.textKey,
        repeat = step.repeat,
        speed = step.speed,
        gapMs = step.gapMs,
        languageCode = step.languageCode,
    )

    /**
     * A step the enum does not recognise yields `null` and is dropped by the caller. The
     * alternative — mapping an unknown kind to `ayah` — would play the verse where the user
     * expected a translation, which is worse than playing less.
     */
    fun stepOf(dto: PresetStepDto): PresetStep? {
        val kind = PresetStepKind.fromWire(dto.kind) ?: return null
        return PresetStep(
            kind = kind,
            textKey = dto.textKey,
            repeat = dto.repeat,
            speed = dto.speed,
            gapMs = dto.gapMs,
            languageCode = dto.languageCode,
        )
    }

    fun presetOf(dto: PresetDto): Preset = Preset.sanitized(
        clientId = dto.clientId,
        name = dto.name,
        description = dto.description,
        steps = dto.steps.mapNotNull(::stepOf),
        reciterKey = dto.reciterKey,
        autoAdvance = dto.autoAdvance,
        ayahGapMs = dto.ayahGapMs,
        isDefault = dto.isDefault,
        isArchived = dto.isArchived,
        revision = dto.revision ?: 1,
        updatedAtMs = dto.updatedAtMs,
    )

    // ---- local storage: steps and events as JSON ------------------------------

    fun encodeSteps(steps: List<PresetStep>): String =
        json.encodeToString(steps.map(::toDto))

    fun stepsOf(stepsJson: String?): List<PresetStep> {
        if (stepsJson.isNullOrBlank()) return emptyList()
        val decoded = runCatching { json.decodeFromString<List<PresetStepDto>>(stepsJson) }.getOrNull()
        return decoded?.mapNotNull(::stepOf).orEmpty()
    }

    fun encodeEvents(events: List<PresetEvent>): String =
        json.encodeToString(events.map(::toDto))

    fun eventsOf(eventsJson: String?): List<PresetEvent> {
        if (eventsJson.isNullOrBlank()) return emptyList()
        val decoded = runCatching { json.decodeFromString<List<PresetEventDto>>(eventsJson) }.getOrNull()
        return decoded?.mapNotNull(::eventOf).orEmpty()
    }

    fun toDto(event: PresetEvent): PresetEventDto = PresetEventDto(
        kind = event.kind.wire,
        at = event.at,
        surah = event.surah,
        ayah = event.ayah,
        stepIndex = event.stepIndex,
    )

    fun eventOf(dto: PresetEventDto): PresetEvent? {
        val kind = PresetEventKind.fromWire(dto.kind) ?: return null
        return PresetEvent(kind = kind, at = dto.at, surah = dto.surah, ayah = dto.ayah, stepIndex = dto.stepIndex)
    }

    // ---- sittings -------------------------------------------------------------

    fun toDto(sitting: PresetSitting): PresetSittingDto = PresetSittingDto(
        clientId = sitting.clientId,
        presetClientId = sitting.presetClientId,
        presetName = sitting.presetName,
        startedAt = sitting.startedAt,
        endedAt = sitting.endedAt,
        activeSec = sitting.activeSec,
        fromSurah = sitting.fromSurah,
        fromAyah = sitting.fromAyah,
        toSurah = sitting.toSurah,
        toAyah = sitting.toAyah,
        fromPage = sitting.fromPage,
        toPage = sitting.toPage,
        ayahsCompleted = sitting.ayahsCompleted,
        stepsCompleted = sitting.stepsCompleted,
        playCount = sitting.playCount,
        pauseCount = sitting.pauseCount,
        seekCount = sitting.seekCount,
        lastEventAt = sitting.lastEventAt,
        status = sitting.status.wire,
        reciterKey = sitting.reciterKey,
        speed = sitting.speed,
        events = sitting.events.map(::toDto),
    )

    fun sittingOf(dto: PresetSittingDto): PresetSitting = PresetSitting(
        clientId = dto.clientId,
        presetClientId = dto.presetClientId,
        presetName = dto.presetName,
        startedAt = dto.startedAt,
        endedAt = dto.endedAt,
        activeSec = dto.activeSec,
        fromSurah = dto.fromSurah,
        fromAyah = dto.fromAyah,
        toSurah = dto.toSurah,
        toAyah = dto.toAyah,
        fromPage = dto.fromPage,
        toPage = dto.toPage,
        ayahsCompleted = dto.ayahsCompleted,
        stepsCompleted = dto.stepsCompleted,
        playCount = dto.playCount,
        pauseCount = dto.pauseCount,
        seekCount = dto.seekCount,
        lastEventAt = dto.lastEventAt,
        status = PresetSittingStatus.fromWire(dto.status),
        reciterKey = dto.reciterKey,
        speed = dto.speed ?: 1f,
        events = dto.events.mapNotNull(::eventOf),
    )

    // ---- coverage -------------------------------------------------------------

    fun toDto(progress: AyahProgress): ProgressEntryDto = ProgressEntryDto(
        surah = progress.surah,
        ayah = progress.ayah,
        globalAyah = progress.globalAyah,
        page = progress.page,
        plays = progress.plays,
        listenedSec = progress.listenedSec,
        firstAt = progress.firstAt,
        lastAt = progress.lastAt,
        lastKind = progress.lastKind,
    )
}
