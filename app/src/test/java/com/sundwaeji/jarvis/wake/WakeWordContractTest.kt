package com.sundwaeji.jarvis.wake

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordContractTest {
    @Test
    fun wakeEngineStatesExposeNoFakeOnlineState() {
        assertEquals(WakeEngineState.UNAVAILABLE, WakeEngineState.valueOf("UNAVAILABLE"))
        assertEquals(WakeEngineState.DETECTED, WakeEngineState.valueOf("DETECTED"))
    }
}
