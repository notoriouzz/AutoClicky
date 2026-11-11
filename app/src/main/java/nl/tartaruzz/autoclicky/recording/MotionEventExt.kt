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

import android.view.MotionEvent

/** Convert the raw motion events received by the recording overlays into [TouchSample]s. */
fun MotionEvent.toTouchSamples(): List<TouchSample> {
    val maskedAction = actionMasked

    return when (maskedAction) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN,
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP -> {
            val pointerIndex = actionIndex
            if (pointerIndex !in 0 until pointerCount) return emptyList()

            listOf(
                TouchSample(
                    pointerId = getPointerId(pointerIndex),
                    action = when (maskedAction) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN -> TouchAction.DOWN
                        else -> TouchAction.UP
                    },
                    x = getX(pointerIndex),
                    y = getY(pointerIndex),
                    eventTimeMs = eventTime,
                )
            )
        }

        MotionEvent.ACTION_MOVE -> (0 until pointerCount).map { pointerIndex ->
            TouchSample(
                pointerId = getPointerId(pointerIndex),
                action = TouchAction.MOVE,
                x = getX(pointerIndex),
                y = getY(pointerIndex),
                eventTimeMs = eventTime,
            )
        }

        MotionEvent.ACTION_CANCEL -> (0 until pointerCount).map { pointerIndex ->
            TouchSample(
                pointerId = getPointerId(pointerIndex),
                action = TouchAction.CANCEL,
                x = getX(pointerIndex),
                y = getY(pointerIndex),
                eventTimeMs = eventTime,
            )
        }

        else -> emptyList()
    }
}
