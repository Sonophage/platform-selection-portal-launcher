package com.psplauncher.feature.artwork.portable

import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Two disc-tag parsers, and they disagree.
 *
 * `DiscTag` (feature-library, the scanner) matches parens AND brackets, `of N` totals, and a
 * trailing `- Disc 2` form. `ArtworkNaming.DISC_TAG` matches parens only:
 *
 * ```
 * \((?:disc|disk|cd)\s*(\d+)[^)]*\)
 * ```
 *
 * The review that found this expected three forms to collide. Running them showed only ONE does,
 * and the reason is worth writing down, because it is not obvious from either regex:
 *
 *  * `[Disc 2]` — **collides.** `TAG_GROUPS` strips bracket groups as release tags, and `DISC_TAG`
 *    never sees brackets, so the number is dropped entirely. Disc 2's box art is written over
 *    disc 1's and nothing errors.
 *  * `- Disc 2` — safe, by accident. There is no group to strip, so "disc 2" survives into the
 *    base slug as `-disc-2`. Correct for a completely different reason than the paren form's
 *    `-disc2` suffix.
 *  * `(Disc 2 of 3)` — safe. `DISC_TAG`'s trailing `[^)]*` swallows the "of 3".
 *
 * **Why the failing one is @Ignore'd rather than fixed.** `ArtworkNaming.NORMALIZATION_VERSION`
 * is 1 and the file's own KDoc says it must not change once shipped: the slug names a folder on
 * the user's SD card, so changing it orphans every asset already filed under the old name. The
 * fix is a version bump plus a re-slug pass — a decision, not a patch.
 *
 * It is pinned now because it gets more expensive every day. Every artwork file a user
 * accumulates makes the migration bigger. Remove the @Ignore in the same commit as the bump.
 *
 * See docs/plans/council-review-remediation-plan.md, task 1.5.
 */
class ArtworkNamingDiscTagTest {

    @Test
    fun `the paren form works today, which is why the gap is easy to miss`() {
        // The control, and the form most dumps use — which is exactly why nobody noticed.
        assertNotEquals(
            ArtworkNaming.slug("Final Fantasy VII (USA) (Disc 1)"),
            ArtworkNaming.slug("Final Fantasy VII (USA) (Disc 2)"),
        )
        assertEquals("final-fantasy-vii-disc2", ArtworkNaming.slug("Final Fantasy VII (USA) (Disc 2)"))
    }

    @Test
    fun `a trailing disc tag is safe, but by accident rather than by design`() {
        // Worth pinning precisely BECAUSE it is accidental. Nothing strips an ungrouped
        // "- Disc 2", so the number survives into the base as `-disc-2` — note the hyphen, which
        // the paren form does not produce. Anyone who later teaches TAG_GROUPS about ungrouped
        // tags would silently turn this into the bracket bug below.
        assertEquals("final-fantasy-vii-disc-1", ArtworkNaming.slug("Final Fantasy VII - Disc 1"))
        assertEquals("final-fantasy-vii-disc-2", ArtworkNaming.slug("Final Fantasy VII - Disc 2"))
    }

    @Test
    fun `an of-N disc tag keeps its own number`() {
        assertEquals("parasite-eve-ii-disc2", ArtworkNaming.slug("Parasite Eve II (Disc 2 of 3)"))
    }

    @Test
    @Ignore("ArtworkNaming.NORMALIZATION_VERSION is frozen at 1; fixing this needs a re-slug pass")
    fun `a bracketed disc tag must not collide with disc one`() {
        // THE REAL ONE. The scanner reads [Disc 2] and sets discNumber = 2; TAG_GROUPS strips the
        // bracket group as a release tag and DISC_TAG only looks at parens, so both discs file
        // their box art under "panzer-dragoon-saga" and the second overwrites the first.
        assertNotEquals(
            ArtworkNaming.slug("Panzer Dragoon Saga [Disc 1]"),
            ArtworkNaming.slug("Panzer Dragoon Saga [Disc 2]"),
        )
    }
}
