package ir.hawraa.quran.data.local

/**
 * Sync state machine for a locally recorded row.
 * pending → syncing → synced | failed. Failed rows stay queued for retry.
 */
enum class SyncStatus(val key: String) {
    PENDING("pending"),
    SYNCING("syncing"),
    SYNCED("synced"),
    FAILED("failed"),
    ;

    companion object {
        fun fromKey(key: String): SyncStatus = entries.firstOrNull { it.key == key } ?: PENDING
    }
}