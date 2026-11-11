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

/**
 * Collects [TouchSample]s and groups them into [RecordedStroke]s.
 *
 * A stroke contains every finger pressed at the same time: it is closed once all of them are released. This is the
 * representation the Smart AutoClicker (Klick'r) engine uses for a [com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction.DumbGesture].
 */
class StrokeCollector {

    private val activeFingers = LinkedHashMap<Int, MutableList<RecordedPoint>>()
    private val finishedFingers = mutableListOf<RecordedFinger>()
    private val strokes = mutableListOf<RecordedStroke>()

    /** Fingers currently pressed. */
    val isCollecting: Boolean get() = activeFingers.isNotEmpty()

    fun onSample(sample: TouchSample) {
        when (sample.action) {
            TouchAction.DOWN -> onPointerDown(sample)
            TouchAction.MOVE -> onPointerMove(sample)
            TouchAction.UP -> onPointerUp(sample)
            TouchAction.CANCEL -> onPointerCancel(sample)
        }
    }

    fun onSamples(samples: List<TouchSample>) {
        samples.forEach(::onSample)
    }

    /** All the strokes collected so far, including the one being recorded. */
    fun getStrokes(): List<RecordedStroke> {
        val inProgress = activeFingers.values
            .filter { points -> points.isNotEmpty() }
            .map { points -> RecordedFinger(points.toList()) }

        return if (inProgress.isEmpty()) {
            strokes.toList()
        } else {
            strokes + RecordedStroke(finishedFingers + inProgress)
        }
    }

    private fun onPointerDown(sample: TouchSample) {
        activeFingers[sample.pointerId] = mutableListOf(
            RecordedPoint(sample.x, sample.y, sample.eventTimeMs)
        )
    }

    private fun onPointerMove(sample: TouchSample) {
        activeFingers[sample.pointerId]?.add(RecordedPoint(sample.x, sample.y, sample.eventTimeMs))
    }

    private fun onPointerUp(sample: TouchSample) {
        val points = activeFingers.remove(sample.pointerId) ?: return
        points.add(RecordedPoint(sample.x, sample.y, sample.eventTimeMs))
        finishedFingers.add(RecordedFinger(points))

        if (activeFingers.isEmpty()) {
            strokes.add(RecordedStroke(finishedFingers.toList()))
            finishedFingers.clear()
        }
    }

    private fun onPointerCancel(sample: TouchSample) {
        activeFingers.remove(sample.pointerId)
        if (activeFingers.isEmpty()) {
            finishedFingers.clear()
        }
    }
}
