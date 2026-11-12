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

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.util.Log

/**
 * Builds the [GestureDescription] sent back to the app by the relay overlay from the plan of [RelayGesturePlanner].
 *
 * A stroke with a single point and a duration is a press that doesn't move: the framework holds the pointer down at that
 * position for the whole duration, without emitting a single move event.
 */
internal object RelayGestureBuilder {

    fun build(specs: List<RelayStrokeSpec>): GestureDescription? {
        if (specs.isEmpty()) return null

        val builder = GestureDescription.Builder()

        specs.forEach { spec ->
            val firstPoint = spec.points.first()
            val path = Path().apply {
                moveTo(firstPoint.x, firstPoint.y)
                spec.points.drop(1).forEach { point -> lineTo(point.x, point.y) }
            }

            builder.addStroke(
                StrokeDescription(path, spec.startTimeMs, spec.durationMs)
            )
        }

        return try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Can't build the gesture to forward to the app", e)
            null
        }
    }

    private const val TAG = "RelayGestureBuilder"
}
