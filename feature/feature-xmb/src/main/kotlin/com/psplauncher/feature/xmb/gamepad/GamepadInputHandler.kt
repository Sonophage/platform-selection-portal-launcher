package com.psplauncher.feature.xmb.gamepad

import android.os.SystemClock
import android.view.InputDevice
import com.psplauncher.core.data.repository.ControllerRegistry
import com.psplauncher.core.data.repository.RemapCoordinator
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.GamepadMappings
import com.psplauncher.core.domain.model.ScrollSpeed
import com.psplauncher.core.domain.model.StickSensitivity
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val STICK_DEAD_ZONE_FLOOR = 0.35f

private const val STICK_RELEASE_FACTOR = 0.6f

private const val HAT_DEAD_ZONE = 0.5f

private const val DUPLICATE_WINDOW_MS = 80L

private const val STICK_FULL_TILT_RAMP_FACTOR = 2

private data class RepeatTuning(
    val initialDelayMs: Long,
    val baseIntervalMs: Long,
    val fastIntervalMs: Long,
    val rampSteps: Int,
)

private fun ScrollSpeed.tuning(): RepeatTuning = when (this) {
    ScrollSpeed.RELAXED  -> RepeatTuning(initialDelayMs = 350, baseIntervalMs = 130, fastIntervalMs = 80, rampSteps = 6)
    ScrollSpeed.STANDARD -> RepeatTuning(initialDelayMs = 250, baseIntervalMs = 110, fastIntervalMs = 50, rampSteps = 5)
    ScrollSpeed.FAST     -> RepeatTuning(initialDelayMs = 180, baseIntervalMs = 90,  fastIntervalMs = 35, rampSteps = 4)
}

sealed interface ShoulderHold {
    val action: GamepadAction
    data class Start(override val action: GamepadAction) : ShoulderHold
    data class End(override val action: GamepadAction) : ShoulderHold
}

internal const val SHOULDER_HOLD_MS = 400L

@Singleton
class GamepadInputHandler @Inject constructor(
    private val remapCoordinator: RemapCoordinator,
    private val registry: ControllerRegistry,
) {
    private val _actions = MutableSharedFlow<GamepadAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<GamepadAction> = _actions.asSharedFlow()

    private val _shoulderHolds = MutableSharedFlow<ShoulderHold>(extraBufferCapacity = 8)
    val shoulderHolds: SharedFlow<ShoulderHold> = _shoulderHolds.asSharedFlow()

    private var shoulderJob: Job? = null
    private var shoulderHeld: GamepadAction? = null

    var currentMappings: GamepadMappings = GamepadMappings()

    var scrollSpeed: ScrollSpeed = ScrollSpeed.STANDARD

    var stickSensitivity: StickSensitivity = StickSensitivity.STANDARD

    var scope: CoroutineScope? = null

    var bypassToComposeFocus: Boolean = false

    private var repeatJob: Job? = null
    private var lastStickAction: GamepadAction? = null

    @Volatile private var stickMagnitude: Float = 0f

    private var prevHatX: Float = 0f
    private var prevHatY: Float = 0f

    private val lastDirectionalEmitAt = mutableMapOf<GamepadAction, Long>()

    internal var clock: () -> Long = SystemClock::uptimeMillis

    fun onKeyEvent(event: KeyEvent): Boolean {
        remapCoordinator.captureNextKey?.let { capture ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                remapCoordinator.captureNextKey = null
                capture(event.keyCode)
            }
            return true
        }

        val action = currentMappings.actionFor(event.keyCode) ?: return false
        registry.markActive(event.deviceId)

        if (bypassToComposeFocus && action != GamepadAction.BACK) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    if (action.isDirectional() && isDuplicateDirection(action)) return true

                    if (action.isDirectional()) {
                        startRepeat(action)
                    }

                    if (action.isShoulder()) {
                        armShoulderHold(action)
                        return true
                    }
                    emit(action, physical = true)
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                if (action.isDirectional()) cancelRepeat()
                if (action.isShoulder()) releaseShoulder(action)
                true
            }
            else -> false
        }
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
            return false
        }
        if (event.action != MotionEvent.ACTION_MOVE) return false
        registry.markActive(event.deviceId)

        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val stickAction = stickDirection(x, y, stickFlatFor(event.deviceId))
        val hatAction = hatDirection(hatX, hatY)

        val motionAction = hatAction ?: stickAction

        stickMagnitude = if (motionAction != null) maxOf(abs(x), abs(y), abs(hatX), abs(hatY)) else 0f

        if (motionAction != lastStickAction) {
            cancelRepeat()
            lastStickAction = motionAction
            if (motionAction != null && !isDuplicateDirection(motionAction)) {
                startRepeat(motionAction)
                emit(motionAction, physical = true)
            }
        }

        prevHatX = hatX
        prevHatY = hatY
        return motionAction != null
    }

    fun emitAction(action: GamepadAction) = emit(action)

    private fun emit(action: GamepadAction, physical: Boolean = false) {
        if (physical && action.isDirectional()) lastDirectionalEmitAt[action] = clock()
        _actions.tryEmit(action)
        Timber.v("Gamepad action: $action")
    }

    private fun startRepeat(action: GamepadAction) {
        val s = scope ?: return
        startRepeating(action, s)
    }

    fun startRepeating(action: GamepadAction, s: CoroutineScope) {
        repeatJob?.cancel()
        repeatJob = s.launch {
            val t = scrollSpeed.tuning()
            delay(t.initialDelayMs)
            var step = 0
            while (true) {
                emit(action)
                step++

                val climbed = rampStepFor(step, stickMagnitude, stickSensitivity.fullTilt)
                delay(rampedInterval(climbed, t.baseIntervalMs, t.fastIntervalMs, t.rampSteps))
            }
        }
    }

    fun cancelRepeat() {
        repeatJob?.cancel()
        repeatJob = null
    }

    private fun stickDirection(x: Float, y: Float, flat: Float): GamepadAction? {
        val activation = maxOf(stickSensitivity.deadZone, STICK_DEAD_ZONE_FLOOR, flat)
        val release = activation * STICK_RELEASE_FACTOR

        val strong = when {
            y < -activation -> GamepadAction.NAVIGATE_UP
            y >  activation -> GamepadAction.NAVIGATE_DOWN
            x < -activation -> GamepadAction.NAVIGATE_LEFT
            x >  activation -> GamepadAction.NAVIGATE_RIGHT
            else -> null
        }
        if (strong != null) return strong

        val engaged = lastStickAction ?: return null
        val stillEngaged = when (engaged) {
            GamepadAction.NAVIGATE_UP -> y < -release
            GamepadAction.NAVIGATE_DOWN -> y > release
            GamepadAction.NAVIGATE_LEFT -> x < -release
            GamepadAction.NAVIGATE_RIGHT -> x > release
            else -> false
        }
        return if (stillEngaged) engaged else null
    }

    private fun hatDirection(hatX: Float, hatY: Float): GamepadAction? =
        hatDirectionNewestFirst(hatX, hatY, prevHatX, prevHatY, lastStickAction)

    private fun stickFlatFor(deviceId: Int): Float =
        runCatching { InputDevice.getDevice(deviceId)?.getMotionRange(MotionEvent.AXIS_X)?.flat }
            .getOrNull() ?: 0f

    private fun isDuplicateDirection(action: GamepadAction): Boolean {
        if (!action.isDirectional()) return false
        val now = clock()
        val last = lastDirectionalEmitAt[action]
        return last != null && now - last < DUPLICATE_WINDOW_MS
    }

    private fun armShoulderHold(action: GamepadAction) {
        shoulderJob?.cancel()
        shoulderHeld = null
        shoulderJob = scope?.launch {
            delay(SHOULDER_HOLD_MS)
            shoulderHeld = action
            _shoulderHolds.tryEmit(ShoulderHold.Start(action))
        }
    }

    private fun releaseShoulder(action: GamepadAction) {
        shoulderJob?.cancel()
        shoulderJob = null
        val held = shoulderHeld
        shoulderHeld = null
        if (held != null) _shoulderHolds.tryEmit(ShoulderHold.End(held)) else emit(action, physical = true)
    }

    private fun GamepadAction.isShoulder() =
        this == GamepadAction.PREV_CATEGORY || this == GamepadAction.NEXT_CATEGORY

    private fun GamepadAction.isDirectional() = this in setOf(
        GamepadAction.NAVIGATE_UP,
        GamepadAction.NAVIGATE_DOWN,
        GamepadAction.NAVIGATE_LEFT,
        GamepadAction.NAVIGATE_RIGHT,
    )
}

internal fun hatDirectionNewestFirst(
    hatX: Float,
    hatY: Float,
    prevHatX: Float,
    prevHatY: Float,
    held: GamepadAction?,
    deadZone: Float = HAT_DEAD_ZONE,
): GamepadAction? {
    val xDir = when {
        hatX < -deadZone -> GamepadAction.NAVIGATE_LEFT
        hatX >  deadZone -> GamepadAction.NAVIGATE_RIGHT
        else -> null
    }
    val yDir = when {
        hatY < -deadZone -> GamepadAction.NAVIGATE_UP
        hatY >  deadZone -> GamepadAction.NAVIGATE_DOWN
        else -> null
    }
    if (xDir == null) return yDir
    if (yDir == null) return xDir

    val xIsNew = abs(prevHatX) <= deadZone
    val yIsNew = abs(prevHatY) <= deadZone
    if (xIsNew != yIsNew) return if (xIsNew) xDir else yDir

    return when (held) {
        yDir -> yDir
        xDir -> xDir
        else -> yDir
    }
}

internal fun rampStepFor(repeats: Int, stickMagnitude: Float, fullTilt: Float): Int =
    if (stickMagnitude >= fullTilt) repeats * STICK_FULL_TILT_RAMP_FACTOR else repeats

internal fun rampedInterval(step: Int, base: Long, fast: Long, rampSteps: Int): Long =
    if (step >= rampSteps) fast else base - (base - fast) * step / rampSteps
