package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.data.local.PlayerSettingsCodec
import com.harroyuz.ddrchartviewer.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlayerSettingsTest {
    @Test fun completeConfigurationSurvivesSerialization() {
        val settings = PlayerSettings(3.27f, SpeedMode.HI_SPEED, 421, 650, true, 730, true, true, true, .75f)
        assertEquals(settings, PlayerSettingsCodec.decode(PlayerSettingsCodec.encode(settings), null))
    }
    @Test fun existingHiSpeedMigratesWithoutLosingItsValue() {
        val migrated = PlayerSettingsCodec.decode(null, 3.5f)
        assertEquals(SpeedMode.HI_SPEED, migrated.speedMode)
        assertEquals(3.5f, migrated.speed, 0f)
        assertEquals(SpeedMode.FHS, PlayerSettingsCodec.decode(null, null).speedMode)
        assertEquals(migrated, PlayerSettingsCodec.decode("not-json", 3.5f))
    }
    @Test fun corruptAndOutOfRangeValuesHaveSafeDefaults() {
        val settings = PlayerSettingsCodec.decode("""{"speedMode":"unknown","speed":-3,"fieldHeight":0,"greenNumber":99999,"constantMs":734,"rate":12}""", null)
        assertEquals(SpeedMode.FHS, settings.speedMode)
        assertEquals(.25f, settings.speed, 0f)
        assertEquals(200, settings.fieldHeight)
        assertEquals(9999, settings.greenNumber)
        assertEquals(730, settings.constantMs)
        assertEquals(2f, settings.rate, 0f)
        assertEquals(2f, PlayerSettings(speed = Float.NaN).normalized().speed, 0f)
    }
    @Test fun floatingSpeedKeepsInitialTravelTimeAcrossSongsAndHeights() {
        val settings = PlayerSettings(greenNumber = 450)
        for (bpm in listOf(80.0, 150.0, 300.0)) for (height in listOf(200f, 600f, 1500f)) {
            val pixels = playerPixelsPerBeat(settings, bpm, height, 2.625f)
            assertEquals(.75, height / pixels * 60 / bpm, .00001)
            // A later BPM doubling still doubles scrolling speed; this is not a BPM lock.
            assertEquals(.375, height / pixels * 60 / (bpm * 2), .00001)
        }
    }
    @Test fun hiSpeedKeepsMultiplierAndHundredthPrecision() {
        val settings = PlayerSettings(speedMode = SpeedMode.HI_SPEED, speed = 2f + .01f).normalized()
        assertEquals(2.01f, settings.speed, .00001f)
        assertEquals(playerPixelsPerBeat(settings, 100.0, 400f, 2f), playerPixelsPerBeat(settings, 300.0, 800f, 2f), 0f)
        assertEquals(500f, playerFieldHeight(1000f, 500), 0f)
        assertEquals(200f, playerFieldHeight(1000f, -1), 0f)
    }
    @Test fun constantFadesInAtRequestedTimeWithoutChangingScrollSpeed() {
        assertEquals(0f, constantAlpha(1.1, 1000), 0f)
        assertEquals(0f, constantAlpha(1.0, 1000), .00001f)
        assertEquals(.5f, constantAlpha(.95, 1000), .00001f)
        assertEquals(1f, constantAlpha(.9, 1000), .00001f)
        assertEquals(1f, constantAlpha(-2.0, 1000), 0f)
        val settings = PlayerSettings()
        assertEquals(playerPixelsPerBeat(settings, 120.0, 600f, 2f), playerPixelsPerBeat(settings.copy(constant = true), 120.0, 600f, 2f), 0f)
    }
    @Test fun constantUsesJudgmentTimeDuringStopAndDelay() {
        val t = Timeline(listOf(TimingEvent(0.0, 120.0)), listOf(TimingEvent(4.0, 1.0)), listOf(TimingEvent(6.0, .5)), 0.0, 16.0)
        // The beat is stationary in a stop, but the next note continues fading in by time.
        assertEquals(t.beatAt(2.5), t.beatAt(2.55), 0.0)
        assertEquals(0f, constantAlpha(t.timeAt(5.0) - 2.5, 1000), 0f)
        assertEquals(.5f, constantAlpha(t.timeAt(5.0) - 2.55, 1000), .00001f)
        assertEquals(.5f, constantAlpha(t.timeAt(6.0) - 4.35, 200), .00001f)
    }
    @Test fun rotaryWrapsAnglesAndRespectsDeadZoneAndBounds() {
        val rotary = RotaryAdjustment(500f, 10f, 9999f)
        rotary.move(100f, 0f, 12f, 200f)
        assertEquals(550f, rotary.move(0f, 100f, 12f, 200f), .0001f)
        assertEquals(600f, rotary.move(-100f, 0f, 12f, 200f), .0001f)
        assertEquals(650f, rotary.move(0f, -100f, 12f, 200f), .0001f)
        assertEquals(700f, rotary.move(100f, 0f, 12f, 200f), .0001f)
        rotary.move(0f, 0f, 12f, 200f)
        assertEquals(700f, rotary.move(-100f, 0f, 12f, 200f), .0001f)
        val boundary = RotaryAdjustment(.25f, .25f, 100f)
        boundary.move(100f, 0f, 12f, 1f)
        assertEquals(.25f, boundary.move(0f, -100f, 12f, 1f), 0f)
    }
}
