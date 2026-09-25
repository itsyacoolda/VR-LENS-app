package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGearVrS23PresetValues() {
    val preset = com.example.model.VrSettings.getPreset(com.example.model.VrSettings.PRESET_GEAR_VR_S23)
    assertEquals(com.example.model.VrSettings.PRESET_GEAR_VR_S23, preset.presetName)
    assertTrue("Gear VR preset should have lens curvature enabled", preset.barrelK1 > 0.05f)
    assertTrue("Touch pass through should default to true", preset.touchPassThrough)
  }

  @Test
  fun testCinemaFlatPresetValues() {
    val preset = com.example.model.VrSettings.getPreset(com.example.model.VrSettings.PRESET_CINEMA_FLAT)
    assertEquals(0.0f, preset.barrelK1, 0.001f)
    assertEquals(0.0f, preset.barrelK2, 0.001f)
    assertEquals(0.0f, preset.barrelK3, 0.001f)
  }

  @Test
  fun testLensDistortionAndFocalLength() {
    val gearVr = com.example.model.VrSettings.getPreset(com.example.model.VrSettings.PRESET_GEAR_VR_S23)
    assertEquals(42.0f, gearVr.focalLength, 0.01f)
    assertTrue("K1 should be non-zero", gearVr.barrelK1 > 0f)
    assertTrue("K2 should be non-zero", gearVr.barrelK2 > 0f)
    assertTrue("K3 should be non-zero", gearVr.barrelK3 > 0f)
    assertFalse("Should start unpaused", gearVr.isPaused)
    assertFalse("Should start with touch peek disabled", gearVr.isTouchPeekMode)
  }
}
