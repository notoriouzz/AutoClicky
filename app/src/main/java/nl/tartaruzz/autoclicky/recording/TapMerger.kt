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
 */
object TapMerger {

    /** The outside touches do not carry their duration: use the same default as the quick click scenario. */
    const val TAP_PRESS_DURATION_MS = 50L

    /** A touch and the click of the same gesture never arrive more than this apart. */
    private const val MERGE_DURATION_MS = 400L

    /** Two events of the same gesture, repeated by the watcher or by a parent view, arrive within this window. */
    private const val DUPLICATE_DURATION_MS = 40L

    /** Two positions closer than this are the same point. */
    private const val SAME_POSITION_PX = 4f

    fun merge(touches: List<RecordedTouch>, clicks: List<RecordedViewClick>): List<RecordedAction.Tap> {
        val availableTouches = keepUniqueTouches(touches).toMutableList()
        val taps = mutableListOf<RecordedAction.Tap>()

        keepUniqueClicks(clicks).forEach { click ->
            val matchIndex = availableTouches.indices
                .filter { index -> abs(availableTouches[index].timeMs - click.timeMs) <= MERGE_DURATION_MS }
                .minByOrNull { index -> abs(availableTouches[index].timeMs - click.timeMs) }
            val touch = matchIndex?.let { index -> availableTouches.removeAt(index) }

            val startTimeMs = minOf(click.timeMs, touch?.timeMs ?: click.timeMs)
            val touchPoint = touch?.takeIf { candidate ->
                candidate.hasCoordinates && click.contains(candidate.x, candidate.y)
            }

            taps.add(
                RecordedAction.Tap(
                    startTimeMs = startTimeMs,
                    endTimeMs = startTimeMs + TAP_PRESS_DURATION_MS,
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

    private fun keepUniqueClicks(clicks: List<RecordedViewClick>): List<RecordedViewClick> =
        clicks
            .sortedBy { click -> click.timeMs }
            .fold(mutableListOf<RecordedViewClick>()) { kept, click ->
                val isDuplicate = kept.any { earlier ->
                    abs(earlier.timeMs - click.timeMs) <= DUPLICATE_DURATION_MS &&
                        (earlier.intersects(click) || earlier.isNear(click, SAME_POSITION_PX))
                }
                if (!isDuplicate) kept.add(click)
                kept
            }
}
