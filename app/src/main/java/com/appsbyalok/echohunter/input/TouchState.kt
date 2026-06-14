package com.appsbyalok.echohunter.input

class TouchState {
    var lastTouchX = -100f
    var lastTouchY = -100f

    // Movement Joystick (Raw Data)
    var moveTouchId = -1
    var moveBaseX = 0f
    var moveBaseY = 0f
    var moveCurrentX = 0f
    var moveCurrentY = 0f
    
    // Movement Joystick (Visual/Derived)
    var moveKnobX = 0f
    var moveKnobY = 0f

    // Attack Joystick (Raw Data)
    var attackTouchId = -1
    var attackKnobX = 0f
    var attackKnobY = 0f

    // Trap Menu (State)
    var trapTouchId = -1
}
