package com.alex.aceshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FileFormatTest {
    @Test
    fun formatByteSize_usesBinaryUnits() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("512 B", formatByteSize(512))
        assertEquals("1 KB", formatByteSize(1024))
        assertEquals("1.5 KB", formatByteSize(1536))
        assertEquals("2 MB", formatByteSize(2L * 1024 * 1024))
        assertEquals("", formatByteSize(-1))
    }

    @Test
    fun formatDuration_omitsHoursUntilNeeded() {
        assertEquals("", formatDuration(0))
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("3:02", formatDuration(182_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }

    @Test
    fun formatFileTime_usesShorterFormInTheSameYear() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 15, 42, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sameYear = now.timeInMillis
        assertTrue(formatFileTime(sameYear, sameYear).startsWith("11-3 "))
        val lastYear = Calendar.getInstance().apply {
            timeInMillis = sameYear
            add(Calendar.YEAR, -1)
        }.timeInMillis
        assertTrue(formatFileTime(lastYear, sameYear).startsWith("2025-"))
    }
}
