package com.psplauncher.core.domain.model

import android.view.KeyEvent

enum class ControllerIcon {
    FACE_SOUTH, FACE_EAST, FACE_WEST, FACE_NORTH,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_ALL,
    BUMPER_LEFT, BUMPER_RIGHT, TRIGGER_LEFT, TRIGGER_RIGHT,
    STICK_LEFT, STICK_RIGHT, STICK_LEFT_CLICK, STICK_RIGHT_CLICK,
    START, SELECT, SYSTEM, SHARE,
    TOUCHPAD, TOUCHPAD_LEFT, TOUCHPAD_RIGHT,
    GAME_CHAT, CAMERA, PADDLE_LEFT, PADDLE_RIGHT,
    JOYCON_SL, JOYCON_SR,
}

fun Int.toControllerIcon(): ControllerIcon? = when (this) {
    KeyEvent.KEYCODE_BUTTON_A -> ControllerIcon.FACE_SOUTH
    KeyEvent.KEYCODE_BUTTON_B -> ControllerIcon.FACE_EAST
    KeyEvent.KEYCODE_BUTTON_X -> ControllerIcon.FACE_WEST
    KeyEvent.KEYCODE_BUTTON_Y -> ControllerIcon.FACE_NORTH
    KeyEvent.KEYCODE_DPAD_UP -> ControllerIcon.DPAD_UP
    KeyEvent.KEYCODE_DPAD_DOWN -> ControllerIcon.DPAD_DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> ControllerIcon.DPAD_LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerIcon.DPAD_RIGHT
    KeyEvent.KEYCODE_BUTTON_L1 -> ControllerIcon.BUMPER_LEFT
    KeyEvent.KEYCODE_BUTTON_R1 -> ControllerIcon.BUMPER_RIGHT
    KeyEvent.KEYCODE_BUTTON_L2 -> ControllerIcon.TRIGGER_LEFT
    KeyEvent.KEYCODE_BUTTON_R2 -> ControllerIcon.TRIGGER_RIGHT
    KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerIcon.STICK_LEFT_CLICK
    KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerIcon.STICK_RIGHT_CLICK
    KeyEvent.KEYCODE_BUTTON_START -> ControllerIcon.START
    KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerIcon.SELECT
    KeyEvent.KEYCODE_BUTTON_MODE -> ControllerIcon.SYSTEM
    else -> null
}
