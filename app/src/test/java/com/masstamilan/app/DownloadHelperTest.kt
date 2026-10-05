package com.masstamilan.app

import com.masstamilan.app.core.util.DownloadHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies download filename handling: MediaStore/FAT32-unsafe chars are
 * stripped so downloads never fail on illegal filenames.
 */
class DownloadHelperTest {

    @Test fun `plain names joined with dash`() {
        assertEquals(
            "Munbe Vaa - Naresh Iyer",
            DownloadHelper.sanitizeFileName("Munbe Vaa", "Naresh Iyer")
        )
    }

    @Test fun `illegal chars replaced`() {
        assertEquals(
            "A_B_C - D_E_",
            DownloadHelper.sanitizeFileName("A/B:C", "D*E?")
        )
    }

    @Test fun `dots and dashes preserved`() {
        assertEquals(
            "Jai-Jai-Rama.mp3 - Anirudh",
            DownloadHelper.sanitizeFileName("Jai-Jai-Rama.mp3", "Anirudh")
        )
    }

    @Test fun `blank input falls back to track`() {
        assertEquals("track", DownloadHelper.sanitizeFileName("", ""))
        assertEquals("track", DownloadHelper.sanitizeFileName("   ", ""))
    }

    @Test fun `tamil script preserved`() {
        val out = DownloadHelper.sanitizeFileName("கண்ணே", "Ilaiyaraaja")
        assertTrue(out.contains("கண்ணே"))
    }
}
