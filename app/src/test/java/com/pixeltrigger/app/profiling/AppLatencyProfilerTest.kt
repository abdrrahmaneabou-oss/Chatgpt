package com.pixeltrigger.app.profiling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLatencyProfilerTest {
    @Before fun reset() {
        AppLatencyProfiler.clear()
    }

    @Test fun imageTimestampAbsoluteValueIsNeverUsedAsElapsedAge() {
        AppLatencyProfiler.onSamplerEntry(
            elapsedNs = 1_000_000_000L,
            imageTimestampNs = 100_000_000_000L,
        )
        AppLatencyProfiler.onSamplerExit(1_000_010_000L)

        AppLatencyProfiler.onSamplerEntry(
            elapsedNs = 1_010_000_000L,
            imageTimestampNs = 100_006_000_000L,
        )
        AppLatencyProfiler.onSamplerExit(1_010_020_000L)

        val fire = AppLatencyProfiler.snapshotForFire()
        assertEquals(10_000_000L, fire.samplerEntryGapNs)
        assertEquals(6_000_000L, fire.imageTimestampGapNs)
        assertTrue(AppLatencyProfiler.report().contains("FOREIGN/UNSYNCED"))
        assertTrue(AppLatencyProfiler.report().contains("Absolute frame age is intentionally NOT computed"))
    }

    @Test fun triggerFrameRetainsLargeProcessedFrameGap() {
        AppLatencyProfiler.onSamplerEntry(1_000_000_000L, 50_000_000_000L)
        AppLatencyProfiler.onSamplerExit(1_000_005_000L)
        AppLatencyProfiler.onSamplerEntry(1_080_000_000L, 50_080_000_000L)
        AppLatencyProfiler.onSamplerExit(1_080_006_000L)

        val fire = AppLatencyProfiler.snapshotForFire()
        assertEquals(80_000_000L, fire.samplerEntryGapNs)
        assertEquals(80_000_000L, fire.imageTimestampGapNs)
        assertTrue(AppLatencyProfiler.report().contains("Large gap"))
    }

    @Test fun samplerInternalTimeIsMeasuredInElapsedDomain() {
        AppLatencyProfiler.onSamplerEntry(5_000_000L, 700_000_000_000L)
        AppLatencyProfiler.onSamplerExit(5_125_000L)
        assertEquals(125_000L, AppLatencyProfiler.snapshotForFire().samplerInternalNs)
    }

    @Test fun clearRemovesPreviousCadenceState() {
        AppLatencyProfiler.onSamplerEntry(1_000_000L, 9_000_000L)
        AppLatencyProfiler.onSamplerExit(1_001_000L)
        AppLatencyProfiler.clear()
        val fire = AppLatencyProfiler.snapshotForFire()
        assertEquals(0L, fire.frameOrdinal)
        assertEquals(-1L, fire.samplerEntryGapNs)
        assertEquals(-1L, fire.imageTimestampGapNs)
    }
}
