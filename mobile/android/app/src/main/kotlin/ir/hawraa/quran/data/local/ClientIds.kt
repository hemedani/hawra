package ir.hawraa.quran.data.local

import java.util.Locale
import java.util.UUID

/**
 * A client id that is unique without a network round trip.
 *
 * Millis first so the value sorts by creation time even before the row's own timestamp is
 * read, then a UUID for uniqueness. `Locale.ROOT` is not decoration: the default locale
 * renders digits in Persian/Arabic, and this string is both a Room primary key and the
 * server's idempotency token — non-ASCII digits in it once turned a session id into
 * something the backend could not match.
 */
fun newClientId(now: Long): String = String.format(Locale.ROOT, "%013d-%s", now, UUID.randomUUID())
