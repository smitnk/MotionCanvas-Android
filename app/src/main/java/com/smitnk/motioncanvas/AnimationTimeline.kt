package com.smitnk.motioncanvas

enum class TimelineLoopMode { LOOP, ONCE, PING_PONG }

data class TimelineState(
    val frame: Int = 0,
    val playing: Boolean = false,
    val direction: Int = 1,
    val fps: Int = 12,
    val speed: Float = 1f,
    val loopMode: TimelineLoopMode = TimelineLoopMode.LOOP
) {
    fun nextFrame(frameCount: Int): TimelineState {
        if (frameCount <= 0) return copy(frame = 0, playing = false)
        if (frameCount == 1) return copy(frame = 0, playing = loopMode != TimelineLoopMode.ONCE)
        var next = frame + direction
        var nextDirection = direction
        var stillPlaying = playing
        when {
            next in 0 until frameCount -> Unit
            loopMode == TimelineLoopMode.LOOP -> next = if (next < 0) frameCount - 1 else 0
            loopMode == TimelineLoopMode.PING_PONG -> {
                nextDirection = -direction
                next = frame + nextDirection
            }
            else -> {
                next = if (next < 0) 0 else frameCount - 1
                stillPlaying = false
            }
        }
        return copy(frame = next, direction = nextDirection, playing = stillPlaying)
    }

    fun frameDelayMillis(): Long =
        (1000f / (fps.coerceIn(1, 60) * speed.coerceIn(0.1f, 4f))).toLong().coerceAtLeast(1L)
}
