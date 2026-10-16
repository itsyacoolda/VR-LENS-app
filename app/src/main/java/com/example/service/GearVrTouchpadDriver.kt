package com.example.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.VrPreferencesRepository
import com.example.model.VrSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Unified Gear VR Touchpad & External Input Driver.
 * Supports:
 * 1. Samsung Gear VR USB-C HID Touchpad & Buttons (SM-R323 / SM-R324 / SM-R325, Vendor ID 0x04E8).
 * 2. Standard Android HID Touchpad / Mouse / Pointer events (SOURCE_TOUCHPAD / SOURCE_MOUSE).
 * 3. On-screen / Edge VR Trackpad gestures for moving the 3D Stereoscopic VR Cursor and clicking/scrolling.
 */
class GearVrTouchpadDriver(
    private val context: Context,
    private val onHapticFeedback: (Long) -> Unit
) {
    private val tag = "GearVrTouchpadDriver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsRepo = VrPreferencesRepository.getInstance(context)
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

    private var usbReadJob: Job? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null

    // Touchpad gesture tracking state
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var maxMoveDistance = 0f
    private var lastTapTime = 0L
    private var hasTriggeredLongPress = false
    private var lastScrollDispatchTime = 0L

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    scanAndConnectGearVrUsb()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    stopUsbReader()
                    updateHardwareStatus()
                }
                ACTION_USB_PERMISSION -> {
                    scanAndConnectGearVrUsb()
                }
            }
        }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            updateHardwareStatus()
        }
        override fun onInputDeviceRemoved(deviceId: Int) {
            updateHardwareStatus()
        }
        override fun onInputDeviceChanged(deviceId: Int) {
            updateHardwareStatus()
        }
    }

    fun start() {
        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            inputManager.registerInputDeviceListener(inputDeviceListener, null)
            updateHardwareStatus()
            scanAndConnectGearVrUsb()
        } catch (e: Exception) {
            Log.e(tag, "Error starting GearVrTouchpadDriver: ${e.message}")
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        try {
            inputManager.unregisterInputDeviceListener(inputDeviceListener)
        } catch (_: Exception) {}
        stopUsbReader()
        scope.cancel()
    }

    private fun updateHardwareStatus() {
        val deviceIds = inputManager.inputDeviceIds
        var foundTouchpadOrGearVr = false
        var deviceName = "Virtual + Gear VR Touchpad Ready"

        for (id in deviceIds) {
            val dev = inputManager.getInputDevice(id) ?: continue
            val sources = dev.sources
            val isTouchpad = (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val nameLower = dev.name.lowercase()
            if (nameLower.contains("gear") || nameLower.contains("samsung") || isTouchpad || isMouse || isGamepad) {
                if (!nameLower.contains("sec_touchscreen") && !nameLower.contains("virtual")) {
                    foundTouchpadOrGearVr = true
                    deviceName = dev.name
                    break
                }
            }
        }

        val usbConnected = usbManager.deviceList.values.any {
            it.vendorId == SAMSUNG_VENDOR_ID || it.productName?.lowercase()?.contains("gear") == true
        }

        _isHardwareDetected.value = foundTouchpadOrGearVr || usbConnected
        _detectedDeviceName.value = if (usbConnected) "Samsung Gear VR USB-C Touchpad" else deviceName
    }

    private fun scanAndConnectGearVrUsb() {
        val devices = usbManager.deviceList.values
        val gearVrDevice = devices.firstOrNull { device ->
            device.vendorId == SAMSUNG_VENDOR_ID &&
                    (device.productId in GEAR_VR_PRODUCT_IDS ||
                            device.productName?.lowercase()?.contains("gear") == true ||
                            hasHidInterface(device))
        } ?: return

        updateHardwareStatus()

        if (!usbManager.hasPermission(gearVrDevice)) {
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) },
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(gearVrDevice, permissionIntent)
            return
        }

        startUsbReader(gearVrDevice)
    }

    private fun hasHidInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_HID) {
                return true
            }
        }
        return false
    }

    private fun startUsbReader(device: UsbDevice) {
        if (usbReadJob?.isActive == true) return

        usbReadJob = scope.launch {
            try {
                var targetInterface: UsbInterface? = null
                var targetEndpoint: UsbEndpoint? = null

                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    for (e in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(e)
                        if (ep.direction == UsbConstants.USB_DIR_IN &&
                            (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT || ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK)
                        ) {
                            targetInterface = intf
                            targetEndpoint = ep
                            break
                        }
                    }
                    if (targetEndpoint != null) break
                }

                val intf = targetInterface ?: return@launch
                val endpoint = targetEndpoint ?: return@launch
                val conn = usbManager.openDevice(device) ?: return@launch

                if (!conn.claimInterface(intf, true)) {
                    conn.close()
                    return@launch
                }

                usbConnection = conn
                usbInterface = intf
                _isHardwareDetected.value = true
                _detectedDeviceName.value = "Gear VR USB-C Touchpad Connected"

                val buffer = ByteArray(endpoint.maxPacketSize.coerceAtLeast(64))
                var prevPadX = -1
                var prevPadY = -1
                var prevTouching = false
                var prevBackBtn = false
                var prevHomeBtn = false
                var prevVolUpBtn = false
                var prevVolDownBtn = false
                var touchStartTime = 0L
                var totalDelta = 0f

                while (isActive) {
                    val bytesRead = conn.bulkTransfer(endpoint, buffer, buffer.size, 100)
                    if (bytesRead >= 34 && prefsRepo.getSettings().gearVrTouchpadEnabled) {
                        // Decode Gear VR 64-byte HID report touchpad coordinates (10-bit X/Y in bytes 31..33)
                        val padX = ((buffer[31].toInt() and 0xFF) shl 2) or ((buffer[32].toInt() and 0xC0) ushr 6)
                        val padY = ((buffer[32].toInt() and 0x3F) shl 4) or ((buffer[33].toInt() and 0xF0) ushr 4)
                        val buttons = buffer[30].toInt() and 0xFF
                        val isTouching = (padX > 0 && padY > 0) || (buttons and 0x01) != 0
                        val backPressed = (buttons and 0x02) != 0
                        val homePressed = (buttons and 0x04) != 0
                        val volUpPressed = (buttons and 0x08) != 0
                        val volDownPressed = (buttons and 0x10) != 0

                        if (backPressed && !prevBackBtn) {
                            onHapticFeedback(35L)
                            VrAccessibilityService.instance?.triggerBackButton()
                        }
                        prevBackBtn = backPressed

                        if (homePressed && !prevHomeBtn) {
                            onHapticFeedback(35L)
                            VrAccessibilityService.instance?.triggerHomeButton()
                        }
                        prevHomeBtn = homePressed

                        if (volUpPressed && !prevVolUpBtn) {
                            onHapticFeedback(30L)
                            VrAccessibilityService.instance?.handleVolumeUpButton()
                        }
                        prevVolUpBtn = volUpPressed

                        if (volDownPressed && !prevVolDownBtn) {
                            onHapticFeedback(30L)
                            VrAccessibilityService.instance?.handleVolumeDownButton()
                        }
                        prevVolDownBtn = volDownPressed

                        if (isTouching) {
                            if (!prevTouching) {
                                touchStartTime = SystemClock.uptimeMillis()
                                totalDelta = 0f
                            } else if (prevPadX > 0 && prevPadY > 0) {
                                val dx = (padX - prevPadX) / 320f
                                val dy = (padY - prevPadY) / 320f
                                totalDelta += hypot(dx, dy)
                                val currentMode = prefsRepo.getSettings().touchpadMode
                                when (currentMode) {
                                    VrSettings.TOUCHPAD_MODE_POINTER -> {
                                        if (abs(dx) > 0.001f || abs(dy) > 0.001f) {
                                            prefsRepo.moveCursorByDelta(dx, dy)
                                        }
                                    }
                                    VrSettings.TOUCHPAD_MODE_SCROLL -> {
                                        VrAccessibilityService.instance?.dispatchScrollAtCursor(dx * 0.35f, dy * 0.35f)
                                    }
                                    VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> {
                                        if (abs(dy) > 0.002f) prefsRepo.adjustScale(-dy * 0.25f)
                                        if (abs(dx) > 0.002f) prefsRepo.adjustIpd(dx * 0.25f)
                                    }
                                }
                            }
                            prevPadX = padX
                            prevPadY = padY
                        } else if (prevTouching) {
                            val elapsed = SystemClock.uptimeMillis() - touchStartTime
                            val now = SystemClock.uptimeMillis()
                            if (elapsed < 480L && totalDelta < 0.08f) {
                                if (now - lastTapTime < 380L) {
                                    // Double Tap on physical Gear VR Pad -> Recenter!
                                    lastTapTime = 0L
                                    prefsRepo.recenterCursor()
                                    onHapticFeedback(25L)
                                    scope.launch {
                                        delay(70L)
                                        onHapticFeedback(35L)
                                    }
                                } else {
                                    lastTapTime = now
                                    triggerTrackpadClick()
                                }
                            } else if (prefsRepo.getSettings().touchpadMode == VrSettings.TOUCHPAD_MODE_SCROLL && totalDelta >= 0.08f) {
                                onHapticFeedback(20L)
                            }
                            prevPadX = -1
                            prevPadY = -1
                        }
                        prevTouching = isTouching
                    } else if (bytesRead < 0) {
                        delay(16)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "USB HID reader stopped: ${e.message}")
            }
        }
    }

    private fun stopUsbReader() {
        usbReadJob?.cancel()
        usbReadJob = null
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        usbInterface = null
    }

    /**
     * Handles MotionEvents from either the Gear VR Touchpad (SOURCE_TOUCHPAD / SOURCE_MOUSE)
     * or the VR Trackpad overlay surface.
     */
    fun handleTouchpadMotionEvent(
        event: MotionEvent,
        surfaceWidth: Int,
        surfaceHeight: Int
    ): Boolean {
        val w = surfaceWidth.coerceAtLeast(1).toFloat()
        val h = surfaceHeight.coerceAtLeast(1).toFloat()
        val settings = prefsRepo.getSettings()

        // Handle Generic Motion (e.g., Gear VR touchpad hover move or scroll wheel)
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val normX = (event.x / w).coerceIn(0.05f, 0.95f)
            val normY = (event.y / h).coerceIn(0.05f, 0.95f)
            prefsRepo.setCursorPosition(normX, normY)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (abs(vScroll) > 0.01f || abs(hScroll) > 0.01f) {
                VrAccessibilityService.instance?.dispatchScrollAtCursor(
                    dxNorm = hScroll * 0.18f,
                    dyNorm = -vScroll * 0.18f
                )
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                touchDownTime = SystemClock.uptimeMillis()
                maxMoveDistance = 0f
                hasTriggeredLongPress = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                val totalDist = hypot(event.x - touchStartX, event.y - touchStartY)
                if (totalDist > maxMoveDistance) {
                    maxMoveDistance = totalDist
                }

                val elapsed = SystemClock.uptimeMillis() - touchDownTime
                // Long-Press (Hold 480ms without moving) -> Mode Switch (Pointer <-> Scroll)
                if (!hasTriggeredLongPress && maxMoveDistance < 35f && elapsed > 480L) {
                    hasTriggeredLongPress = true
                    onHapticFeedback(55L)
                    val nextMode = if (settings.touchpadMode == VrSettings.TOUCHPAD_MODE_POINTER) {
                        VrSettings.TOUCHPAD_MODE_SCROLL
                    } else {
                        VrSettings.TOUCHPAD_MODE_POINTER
                    }
                    prefsRepo.setTouchpadMode(nextMode)
                }

                when (settings.touchpadMode) {
                    VrSettings.TOUCHPAD_MODE_POINTER -> {
                        // Move 3D Stereoscopic VR Cursor smoothly across both eyes
                        prefsRepo.moveCursorByDelta(dx / (w * 0.45f), dy / (h * 0.45f))
                    }
                    VrSettings.TOUCHPAD_MODE_SCROLL -> {
                        // Real-time continuous fluid scrolling for websites & apps
                        val now = SystemClock.uptimeMillis()
                        if (now - lastScrollDispatchTime > 32L && (abs(dx) > 1.2f || abs(dy) > 1.2f)) {
                            lastScrollDispatchTime = now
                            VrAccessibilityService.instance?.dispatchScrollAtCursor(
                                dxNorm = dx / (w * 0.45f),
                                dyNorm = dy / (h * 0.45f)
                            )
                        }
                    }
                    VrSettings.TOUCHPAD_MODE_ZOOM_IPD -> {
                        // In-headset comfort tuning: drag up/down = zoom, drag left/right = eye comfort IPD
                        if (abs(dy) > 1.2f) {
                            prefsRepo.adjustScale(-dy / (h * 2.0f))
                        }
                        if (abs(dx) > 1.2f) {
                            prefsRepo.adjustIpd(dx / (w * 2.5f))
                        }
                    }
                    else -> {
                        prefsRepo.moveCursorByDelta(dx / (w * 0.45f), dy / (h * 0.45f))
                    }
                }

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val duration = SystemClock.uptimeMillis() - touchDownTime
                val totalDx = event.x - touchStartX
                val totalDy = event.y - touchStartY
                val now = SystemClock.uptimeMillis()

                if (hasTriggeredLongPress) {
                    // Already handled mode switch
                    return true
                }

                if (maxMoveDistance < 45f && duration < 550L) {
                    if (now - lastTapTime < 380L) {
                        // Double Tap -> Recenter 3D Reticle to Center (50%, 50%)!
                        lastTapTime = 0L
                        prefsRepo.recenterCursor()
                        onHapticFeedback(25L)
                        scope.launch {
                            delay(70L)
                            onHapticFeedback(35L)
                        }
                    } else {
                        // Single Tap -> Click Effect + Dispatch Tap
                        lastTapTime = now
                        triggerTrackpadClick()
                    }
                } else if (settings.touchpadMode == VrSettings.TOUCHPAD_MODE_POINTER &&
                    totalDx < -(w * 0.40f) && abs(totalDy) < (h * 0.20f) && duration < 320L
                ) {
                    // Quick flick left -> Back navigation in VR!
                    onHapticFeedback(35L)
                    prefsRepo.setTrackpadHudNotice("🔙 Flick: Back Navigation")
                    VrAccessibilityService.instance?.triggerBackButton()
                } else if (settings.touchpadMode == VrSettings.TOUCHPAD_MODE_SCROLL && maxMoveDistance >= 40f) {
                    // Swipe fling in Scroll Mode
                    onHapticFeedback(20L)
                    VrAccessibilityService.instance?.dispatchScrollAtCursor(
                        dxNorm = totalDx / (w * 0.4f),
                        dyNorm = totalDy / (h * 0.4f)
                    )
                } else if (settings.touchpadMode == VrSettings.TOUCHPAD_MODE_ZOOM_IPD) {
                    val s = prefsRepo.getSettings()
                    prefsRepo.setTrackpadHudNotice("✨ Locked In: ${(s.scale * 100).toInt()}% Size • Split ${(s.ipdOffset * 100).toInt()}")
                }
                return true
            }
        }
        return false
    }

    fun triggerTrackpadClick() {
        onHapticFeedback(30L)
        val s = prefsRepo.getSettings()
        prefsRepo.setCursorClicking(true)
        prefsRepo.setLastDetectedGesture(
            "🎯 Trackpad Clicked at (${(s.cursorX * 100).toInt()}%, ${(s.cursorY * 100).toInt()}%)"
        )
        scope.launch {
            delay(220L)
            prefsRepo.setCursorClicking(false)
        }
        val a11y = VrAccessibilityService.instance
        if (a11y != null) {
            a11y.dispatchTapAtCursor()
        } else {
            MainActivity.dispatchInAppTouchAtNormalized(s.cursorX, s.cursorY)
        }
    }

    companion object {
        private const val SAMSUNG_VENDOR_ID = 0x04E8
        private val GEAR_VR_PRODUCT_IDS = setOf(0xA500, 0xA501, 0xA502)
        private const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"

        private val _isHardwareDetected = MutableStateFlow(false)
        val isHardwareDetected: StateFlow<Boolean> = _isHardwareDetected.asStateFlow()

        private val _detectedDeviceName = MutableStateFlow("Gear VR & Virtual Touchpad Ready")
        val detectedDeviceName: StateFlow<String> = _detectedDeviceName.asStateFlow()
    }
}
