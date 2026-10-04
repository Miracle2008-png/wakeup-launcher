package com.wakeup.dna

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPolicyTest {
    private val ok = AudioPolicy.Inputs(true, 0.8f, 0.5f, locked = false, screenOn = true, launcherResumed = true, otherMediaPlaying = false, inCall = false)

    @Test fun playsWhenEverythingAllows() = assertTrue(AudioPolicy.mayPlay(ok))

    @Test fun neverWhileLockedWhateverElseIsTrue() {
        assertFalse(AudioPolicy.mayPlay(ok.copy(locked = true)))
        assertFalse(AudioPolicy.mayPlay(ok.copy(locked = true, masterVolume = 1f, themeVolume = 1f)))
    }

    @Test fun neverWithScreenOffOrWhenNotVisible() {
        assertFalse(AudioPolicy.mayPlay(ok.copy(screenOn = false)))
        assertFalse(AudioPolicy.mayPlay(ok.copy(launcherResumed = false)))
    }

    @Test fun yieldsToOtherAudioAndCalls() {
        assertFalse(AudioPolicy.mayPlay(ok.copy(otherMediaPlaying = true)))
        assertFalse(AudioPolicy.mayPlay(ok.copy(inCall = true)))
    }

    @Test fun userSwitchOffAndZeroVolumeSilence() {
        assertFalse(AudioPolicy.mayPlay(ok.copy(enabled = false)))
        assertFalse(AudioPolicy.mayPlay(ok.copy(masterVolume = 0f)))
        assertFalse(AudioPolicy.mayPlay(ok.copy(themeVolume = 0f)))
    }

    @Test fun exhaustiveLockedTruthTable() {
        // brute force every combination: any with locked = true must be silent
        for (mask in 0 until 128) {
            val i = AudioPolicy.Inputs(mask and 1 != 0, 1f, 1f, locked = true, screenOn = mask and 2 != 0, launcherResumed = mask and 4 != 0, otherMediaPlaying = mask and 8 != 0, inCall = mask and 16 != 0)
            assertFalse("mask $mask", AudioPolicy.mayPlay(i))
        }
    }
}
