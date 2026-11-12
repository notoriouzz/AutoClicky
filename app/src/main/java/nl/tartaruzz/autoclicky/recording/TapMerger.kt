/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 AutoClicky contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.tartaruzz.autoclicky.recording

import kotlin.math.abs
import kotlin.math.roundToInt

/** A tap seen by the outside touch watcher of the recorder: it has a time and, on most devices, a position. */
data class RecordedTouch(
    val x: Float,
    val y: Float,
    val timeMs: Long,
) {

    /** False when the device reported (0,0) instead of the real position, as Samsung does. */
    val hasCoordinates: Boolean get() = x != 0f || y != 0f
}

/** A tap seen through the accessibility click event of a view, with the screen bounds of that view. */
data class RecordedViewClick(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val timeMs: Long,
    /** True for `TYPE_VIEW_LONG_CLICKED`: the view was held down long enough for the app to trigger a long press. */
    val isLongPress: Boolean = false,
) {

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x.roundToInt() in left..right && y.roundToInt() in top..bottom

    fun intersects(other: RecordedViewClick): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun isNear(other: RecordedViewClick, distancePx: Float): Boolean =
        abs(centerX - other.centerX) <= distancePx && abs(centerY - other.centerY) <= distancePx
}

/**
 * Turns the two sources of a tap capture into a single action per gesture.
 *
 * A gesture is reported twice: as an `ACTION_OUTSIDE` touch, which has the timing but no coordinates on some devices,
 * and as the accessibility click of the view that received it, which has the bounds but only the time of the release.
 * Pairing them here, when the capture ends, is what makes fast sequences work: two taps of a quick sequence are less
 * than a hundred milliseconds apart, so merging them while recording used to swallow every tap into a single action.
 *
 * The position of the touch itself is preferred when the device reports it, because it is where the finger actually
 * went; the centre of the clicked view is the fallback, which is what makes the recording useful on a Samsung device.
 *
 * The pairing also gives the press duration of a tap, from its down to the click of the view: a short press stays a
 * click, while a long press is replayed with a press long enough for the app to trigger its long click action again.
 */
object TapMerger {

    /** The outside touches do not always carry their duration: use the same default as the quick click scenario. */
    const val TAP_PRESS_DURATION_MS = 50L

    /** Above this, a press reported as a plain click is shortened: only a long click is meant to be a long press. */
    private const val MAX_CLICK_PRESS_DURATION_MS = 300L

    /** A long press must be replayed at least this long, or the app would not recognise it as one. */
    private const val MIN_LONG_PRESS_DURATION_MS = 700L

    /** Same maximum as the engine: a click can hold for 10 seconds. */
    private const val MAX_PRESS_DURATION_MS = 10_000L

    /** A touch and the click of the same gesture never arrive more than this apart. */
    private const val MERGE_DURATION_MS = 400L

    /** A long click is reported as soon as the press is recognised, well before the finger is released. */
    private const val LONG_PRESS_MERGE_DURATION_MS = 1_500L

    /** Two events of the same gesture, repeated by the watcher or by a parent view, arrive within this window. */
    private const val DUPLICATE_DURATION_MS = 40L

    /** Some apps report both a click and a long click for one long press: they are the same gesture. */
    private const val LONG_CLICK_DUPLICATE_MS = 1_200L

    /** Two positions closer than this are the same point. */
    private const val SAME_POSITION_PX = 4f

    fun merge(touches: List<RecordedTouch>, clicks: List<RecordedViewClick>): List<RecordedAction.Tap> {
        val availableTouches = keepUniqueTouches(touches).toMutableList()
        val taps = mutableListOf<RecordedAction.Tap>()

        keepUniqueClicks(clicks).forEach { click ->
            val pairingWindow = if (click.isLongPress) LONG_PRESS_MERGE_DURATION_MS else MERGE_DURATION_MS
            val matchIndex = availableTouches.indices
                .filter { index -> abs(availableTouches[index].timeMs - click.timeMs) <= pairingWindow }
                .minByOrNull { index -> abs(availableTouches[index].timeMs - click.timeMs) }
            val touch = matchIndex?.let { index -> availableTouches.removeAt(index) }

            val startTimeMs = touch?.timeMs?.takeIf { timeMs -> timeMs <= click.timeMs } ?: click.timeMs
            val touchPoint = touch?.takeIf { candidate ->
                candidate.hasCoordinates && click.contains(candidate.x, candidate.y)
            }

            taps.add(
                RecordedAction.Tap(
                    startTimeMs = startTimeMs,
                    endTimeMs = startTimeMs + click.pressDurationMs(touch),
                    x = touchPoint?.x ?: click.centerX,
                    y = touchPoint?.y ?: click.centerY,
                )
            )
        }

        // Taps that no view reported: a canvas game, or a click the service did not receive.
        availableTouches
            .filter { touch -> touch.hasCoordinates }
            .forEach { touch ->
                taps.add(
                    RecordedAction.Tap(
                        startTimeMs = touch.timeMs,
                        endTimeMs = touch.timeMs + TAP_PRESS_DURATION_MS,
                        x = touch.x,
                        y = touch.y,
                    )
                )
            }

        return taps.sortedBy { tap -> tap.startTimeMs }
    }

    /**
     * How long the finger stayed down, from the touch that started the gesture to the click of the view.
     *
     * The watcher gives the down time, the click the release: their difference is the real press duration. A long click
     * is however reported before the release, so it is stretched to the minimum duration an app needs to recognise a
     * long press again.
     */
    private fun RecordedViewClick.pressDurationMs(touch: RecordedTouch?): Long {
        val heldMs = if (touch != null) (timeMs - touch.timeMs).coerceAtLeast(0L) else 0L

        return when {
            isLongPress -> heldMs.coerceAtLeast(MIN_LONG_PRESS_DURATION_MS)
            else -> heldMs.coerceIn(TAP_PRESS_DURATION_MS, MAX_CLICK_PRESS_DURATION_MS)
        }.coerceAtMost(MAX_PRESS_DURATION_MS)
    }

    private fun keepUniqueTouches(touches: List<RecordedTouch>): List<RecordedTouch> =
        touches
            .sortedBy { touch -> touch.timeMs }
            .fold(mutableListOf<RecordedTouch>()) { kept, touch ->
                val isDuplicate = kept.any { earlier ->
                    abs(earlier.timeMs - touch.timeMs) <= DUPLICATE_DURATION_MS &&
                        abs(earlier.x - touch.x) <= SAME_POSITION_PX &&
                        abs(earlier.y - touch.y) <= SAME_POSITION_PX
                }
                if (!isDuplicate) kept.add(touch)
                kept
            }

    /**
     * Drop the clicks that report the same gesture twice.
     *
     * A long press is kept over the click of the same view: it is the one that tells the press was a long one.
     */
    private fun keepUniqueClicks(clicks: List<RecordedViewClick>): List<RecordedViewClick> =
        clicks
            .sortedBy { click -> click.timeMs }
            .fold(mutableListOf<RecordedViewClick>()) { kept, click ->
                val duplicateIndex = kept.indexOfFirst { earlier -> earlier.reportsSameGesture(click) }
                when {
                    duplicateIndex < 0 -> kept.add(click)
                    click.isLongPress && !kept[duplicateIndex].isLongPress -> kept[duplicateIndex] = click
                }
                kept
            }

    private fun RecordedViewClick.reportsSameGesture(other: RecordedViewClick): Boolean {
        val gapMs = other.timeMs - timeMs
        if (gapMs < 0) return false
        if (!intersects(other) && !isNear(other, SAME_POSITION_PX)) return false

        return if (isLongPress == other.isLongPress) {
            gapMs <= DUPLICATE_DURATION_MS
        } else {
            // A long press is reported as a long click, and by some apps also as a click: one gesture, one action.
            gapMs <= LONG_CLICK_DUPLICATE_MS
        }
    }
}
