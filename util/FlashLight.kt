package com.example.kotlovan.util

import android.content.Context
import android.hardware.camera2.CameraManager

class Flashlight(private val context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String? = try { manager.cameraIdList.firstOrNull() } catch (e: Exception) { null }
    private var on = false

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return try {
            manager.setTorchMode(id, !on); on = !on; on
        } catch (e: Exception) { false }
    }
    fun off() {
        val id = cameraId ?: return
        try { manager.setTorchMode(id, false) } catch (_: Exception) {}
        on = false
    }
}