package com.psplauncher.core.data.saf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafSiblingDocumentIdTest {
    @Test
    fun `swaps the last path segment`() {
        assertEquals(
            "primary:Roms/psx/Game/Game.bin",
            safSiblingDocumentId("primary:Roms/psx/Game/Game.cue", "Game.bin"),
        )
    }

    @Test
    fun `keeps the volume prefix for a file directly under the root`() {
        assertEquals("primary:Game.bin", safSiblingDocumentId("primary:Game.cue", "Game.bin"))
        assertEquals("408C-3861:Game.bin", safSiblingDocumentId("408C-3861:Game.cue", "Game.bin"))
    }

    @Test
    fun `splits on the last slash, not the volume colon, for a deep path`() {
        assertEquals(
            "408C-3861:Games/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).bin",
            safSiblingDocumentId(
                "408C-3861:Games/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue",
                "Parasite Eve II (Disc 2).bin",
            ),
        )
    }

    @Test
    fun `preserves the sibling name's case`() {
        assertEquals(
            "primary:Roms/psx/PE2/Parasite Eve II (Disc 2).bin",
            safSiblingDocumentId("primary:Roms/psx/PE2/Parasite Eve II (Disc 2).cue", "Parasite Eve II (Disc 2).bin"),
        )
    }

    @Test
    fun `returns null for an id with no separator to anchor on`() {
        assertNull(safSiblingDocumentId("opaque-doc-id", "Game.bin"))
    }

    @Test
    fun `accepts a plain sibling name`() {
        assertTrue(isSafeSiblingName("Game.bin"))
        assertTrue(isSafeSiblingName("Parasite Eve II (Disc 2).bin"))
        assertTrue(isSafeSiblingName("Track01.RAW"))
    }

    @Test
    fun `rejects a name carrying a path component`() {
        assertFalse(isSafeSiblingName("sub/track02.bin"))
        assertFalse(isSafeSiblingName("../../etc/passwd"))
        assertFalse(isSafeSiblingName("..\\..\\evil.bin"))
        assertFalse(isSafeSiblingName("/absolute/path.bin"))
    }

    @Test
    fun `rejects an empty name`() {
        assertFalse(isSafeSiblingName(""))
    }

    @Test
    fun `rejects a bare parent-directory reference`() {
        assertFalse(isSafeSiblingName(".."))
    }
}
