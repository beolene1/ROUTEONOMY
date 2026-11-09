package com.routeonomy.app.domain

/** Minimal domain state; gear and destination arrival are intentionally independent. */
class DriveSession {
    enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL }
    enum class Arrival { NOT_ARRIVED, ARRIVED }

    private var currentGear = Gear.PARK
    private var currentArrival = Arrival.NOT_ARRIVED
    private var remainingMinutes = 15

    fun gear(): Gear = currentGear
    fun arrival(): Arrival = currentArrival
    fun etaMinutes(): Int = remainingMinutes
    fun isDriving(): Boolean = currentGear != Gear.PARK
    fun canStartQuiz(): Boolean = currentGear == Gear.PARK && currentArrival == Arrival.ARRIVED
    fun setGear(value: Gear) { currentGear = value }
    fun setArrival(value: Arrival) { currentArrival = value }
    fun setEtaMinutes(value: Int) { remainingMinutes = value.coerceAtLeast(1) }
}
