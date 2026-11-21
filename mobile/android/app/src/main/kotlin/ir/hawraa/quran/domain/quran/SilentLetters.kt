package ir.hawraa.quran.domain.quran

/**
 * Finds the letters that are **written but not recited**, so the reader can show them
 * in a lighter shade.
 *
 * Uthmani orthography keeps letters that the recitation drops, and a mushaf that renders
 * them at full strength teaches the wrong pronunciation. Three sources, in order of
 * authority:
 *
 *  1. **The text itself.** The Uthmani encoding marks a silent letter with
 *     U+06DF ARABIC SMALL HIGH ROUNDED ZERO (or U+06E0, the upright rectangular form)
 *     placed after it. That covers the silent wāw of `أُو۟لَٰٓئِكَ` ("ulā'ika") and the
 *     silent alif of the plural ending in `قَالُوا۟` ("qālū"), which is the bulk of the
 *     cases in the first five pages.
 *  2. **The definite article before a sun letter.** `ٱل` + one of the fourteen ḥurūf
 *     shamsiyya: the lām is assimilated and the connective alif is not read either, so
 *     `ٱلشَّمْس` is "ash-shams", `ٱلصَّلَوٰة` is "aṣ-ṣalāh", `ٱلرَّحْمَٰن` is "ar-Raḥmān".
 *     The text does *not* mark this, so it has to be a rule. The article may sit behind a
 *     proclitic (`وَٱلشَّمْس`, `بِٱللَّهِ`), which is why the check looks past one letter.
 *  3. **A small lexicon** for the classic silent letters that some orthographies leave
 *     unmarked — the wāw of `أُولَٰئِكَ` and `عَمْرٌو`, the alif of `مِائَة`. None of these
 *     occur in pages 1–5 today, but the rule set should not depend on that.
 *
 * A silent letter is muted, never removed: the mushaf must keep the written form intact
 * (that is the point of the script), only its weight changes.
 *
 * Pure: no Android, no text measurement. Indices are character offsets into the string
 * passed in, so the caller can hand them straight to a `SpanStyle`.
 */
object SilentLetters {

    /** U+06DF ARABIC SMALL HIGH ROUNDED ZERO — "this letter is not pronounced". */
    private const val SILENT_ROUNDED_ZERO = '\u06DF'

    /** U+06E0 ARABIC SMALL HIGH UPRIGHT RECTANGULAR ZERO — the rarer variant. */
    private const val SILENT_RECT_ZERO = '\u06E0'

    private const val ALEF_WASLA = '\u0671'
    private const val ALEF = '\u0627'
    private const val LAM = '\u0644'
    private const val TATWEEL = '\u0640'

    /** The fourteen sun letters (ḥurūf shamsiyya) that assimilate the article's lām. */
    val SUN_LETTERS: Set<Char> = setOf(
        'ت', 'ث', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ل', 'ن',
    )

    /** Proclitics that can precede the article inside one word: وَٱل, فَٱل, بِٱل, كَٱل, لِٱل. */
    private val PROCLITICS: Set<Char> = setOf('و', 'ف', 'ب', 'ك', 'ل')

    /**
     * Words whose silent letter the orthography may leave unmarked, keyed by their
     * normalised form, valued by the letter positions to mute. Positions count letters
     * only (marks are dropped), so `أُولَٰئِكَ` → `اولئك` mutes index 1, the wāw.
     */
    private val LEXICON: Map<String, IntArray> = mapOf(
        "اولئك" to intArrayOf(1),
        "اولئكم" to intArrayOf(1),
        "اولئكما" to intArrayOf(1),
        "اولئكهم" to intArrayOf(1),
        "اولي" to intArrayOf(1),
        "اولوا" to intArrayOf(1),
        "مائه" to intArrayOf(1),
        "مائتين" to intArrayOf(1),
        "مائتا" to intArrayOf(1),
        "مائتي" to intArrayOf(1),
        "عمرو" to intArrayOf(3),
    )

    /**
     * Character ranges to render muted, in ascending order and non-overlapping.
     * Empty when every written letter is recited.
     */
    fun ranges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val muted = sortedSetOf<Int>()
        var index = 0
        while (index < text.length) {
            if (text[index].isWhitespace()) {
                index++
                continue
            }
            val start = index
            while (index < text.length && !text[index].isWhitespace()) index++
            collectWord(text, start, index, muted)
        }
        return muted.toRanges()
    }

    private fun collectWord(text: String, start: Int, end: Int, muted: MutableSet<Int>) {
        // 1. The Uthmani marker: the letter it follows is silent.
        for (i in start until end) {
            val c = text[i]
            if (c == SILENT_ROUNDED_ZERO || c == SILENT_RECT_ZERO) {
                previousLetter(text, start, i)?.let(muted::add)
            }
        }

        // 2. ٱل + sun letter, optionally behind a proclitic.
        val first = nextLetter(text, start, end)
        if (first != null) {
            val alif = if (isArticleAlif(text[first])) first else articleAlifAfterProclitic(text, first, end)
            if (alif != null) {
                val lam = nextLetter(text, alif + 1, end)
                if (lam != null && text[lam] == LAM) {
                    val following = nextLetter(text, lam + 1, end)
                    if (following != null && text[following] in SUN_LETTERS) {
                        muted.add(alif)
                        muted.add(lam)
                    }
                }
            }
        }

        // 3. The lexicon, for orthographies that leave the letter unmarked.
        muteLexiconLetters(text, start, end, muted)
    }

    /** `ٱ` (alef wasla) or a plain `ا` used as the article's carrier. */
    private fun isArticleAlif(c: Char): Boolean = c == ALEF_WASLA || c == ALEF

    /**
     * `وَٱل`, `بِٱل`: the article's alif sits one *letter* into the word. Returns the
     * index of that alif, or null when the word does not open with proclitic + article.
     */
    private fun articleAlifAfterProclitic(text: String, firstLetter: Int, end: Int): Int? {
        if (text[firstLetter] !in PROCLITICS) return null
        val candidate = nextLetter(text, firstLetter + 1, end) ?: return null
        return if (isArticleAlif(text[candidate])) candidate else null
    }

    private fun muteLexiconLetters(text: String, start: Int, end: Int, muted: MutableSet<Int>) {
        if (LEXICON.isEmpty()) return
        // Normalise without moving indices: marks are dropped, letters are folded.
        val letters = ArrayList<Pair<Char, Int>>(end - start)
        for (i in start until end) {
            val c = text[i]
            if (isMark(c) || c == TATWEEL) continue
            letters.add(fold(c) to i)
        }
        if (letters.isEmpty()) return
        val key = buildString(letters.size) { letters.forEach { append(it.first) } }
        val positions = LEXICON[key] ?: return
        for (position in positions) {
            letters.getOrNull(position)?.let { muted.add(it.second) }
        }
    }

    /** Folds the alef/yeh/hamza variants so the lexicon keys stay readable. */
    private fun fold(c: Char): Char = when (c) {
        '\u0622', '\u0623', '\u0625', ALEF_WASLA -> ALEF
        '\u0649' -> '\u064A'
        '\u0629' -> '\u0647'
        else -> c
    }

    private fun nextLetter(text: String, from: Int, end: Int): Int? {
        var i = from
        while (i < end) {
            if (!isMark(text[i])) return i
            i++
        }
        return null
    }

    private fun previousLetter(text: String, start: Int, from: Int): Int? {
        var i = from - 1
        while (i >= start) {
            if (!isMark(text[i])) return i
            i--
        }
        return null
    }

    /**
     * Combining marks, as classified for the two ink layers in
     * `tools/build-quran-fonts.py`. A mark is never itself "a silent letter"; it only
     * decorates one.
     */
    fun isMark(c: Char): Boolean {
        val code = c.code
        return code in 0x0610..0x061A ||
            code in 0x064B..0x065F ||
            code == 0x0670 ||
            code in 0x06D6..0x06ED ||
            code in 0x08D3..0x08FF
    }
}

/** Collapses `[3,4,5,9]` into `[3..5, 9..9]` so spans stay few. */
private fun Collection<Int>.toRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val sorted = sorted()
    val ranges = ArrayList<IntRange>(sorted.size)
    var first = sorted.first()
    var last = first
    for (value in sorted.drop(1)) {
        if (value == last + 1) {
            last = value
        } else {
            ranges.add(first..last)
            first = value
            last = value
        }
    }
    ranges.add(first..last)
    return ranges
}
