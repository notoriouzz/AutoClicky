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

import android.graphics.Point

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DUMB_SCENARIO_MAX_DURATION_MINUTES
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbGestureFinger
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbGestureFingerMotion
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.REPEAT_DELAY_MAX_MS

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Converts a recording into the scenario model of the vendored Smart AutoClicker (Klick'r) engine.
 *
 * The engine executes the actions by increasing [DumbAction.priority], and waits [DumbAction.DumbClick.repeatDelayMs]
 * after each of them, so the time between two recorded actions is stored as the delay of the previous one.
 */
object DumbScenarioBuilder {

    fun build(name: String, actions: List<RecordedAction>): DumbScenario {
        val scenarioId = Identifier(TEMP_SCENARIO_ID, asTemporary = true)

        return DumbScenario(
            id = scenarioId,
            name = name,
            dumbActions = actions.mapIndexed { index, action ->
                action.toDumbAction(
                    scenarioId = scenarioId,
                    priority = index,
                    repeatDelayMs = actions.getOrNull(index + 1)
                        ?.let { nextAction -> (nextAction.startTimeMs - action.endTimeMs).coerceAtLeast(0L) }
                        ?: 0L,
                )
            },
            repeatCount = 1,
            isRepeatInfinite = false,
            maxDurationMin = DUMB_SCENARIO_MAX_DURATION_MINUTES,
            isDurationInfinite = true,
            randomize = false,
        )
    }

    private fun RecordedAction.toDumbAction(
        scenarioId: Identifier,
        priority: Int,
        repeatDelayMs: Long,
    ): DumbAction = when (this) {
        is RecordedAction.Tap -> DumbAction.DumbClick(
            id = Identifier(priority.toLong() + 1, asTemporary = true),
            scenarioId = scenarioId,
            name = "Tap ${priority + 1}",
            priority = priority,
            repeatCount = 1,
            isRepeatInfinite = false,
            repeatDelayMs = repeatDelayMs.coerceAtMost(REPEAT_DELAY_MAX_MS),
            position = Point(x.roundToInt(), y.roundToInt()),
            pressDurationMs = durationMs.coerceIn(1L, MAX_PRESS_DURATION_MS),
        )

        is RecordedAction.Gesture -> DumbAction.DumbGesture(
            id = Identifier(priority.toLong() + 1, asTemporary = true),
            scenarioId = scenarioId,
            name = "Gesture ${priority + 1}",
            priority = priority,
            repeatCount = 1,
            isRepeatInfinite = false,
            repeatDelayMs = repeatDelayMs.coerceAtMost(REPEAT_DELAY_MAX_MS),
            fingers = fingers.mapIndexed { fingerIndex, finger ->
                finger.toDumbGestureFinger(
                    gestureStartTimeMs = startTimeMs,
                    id = (fingerIndex + 1).toLong(),
                )
            },
        )
    }

    private fun RecordedFinger.toDumbGestureFinger(
        gestureStartTimeMs: Long,
        id: Long,
    ): DumbGestureFinger {
        val motions = mutableListOf<DumbGestureFingerMotion>()
        val firstPoint = points.first()

        motions.add(
            DumbGestureFingerMotion.Press(
                id = nextMotionId(motions),
                position = Point(firstPoint.x.roundToInt(), firstPoint.y.roundToInt()),
            )
        )

        points
            .drop(1)
            .takeWhile { point -> point.timeMs - gestureStartTimeMs < MAX_GESTURE_DURATION_MS }
            .forEachIndexed { index, point ->
                val previous = points[index]
                val durationMs = (point.timeMs - previous.timeMs).coerceAtLeast(MIN_MOTION_DURATION_MS)

                if (isStill(previous, point)) {
                    motions.add(
                        DumbGestureFingerMotion.Wait(
                            id = nextMotionId(motions),
                            durationMs = durationMs.coerceAtMost(MAX_GESTURE_DURATION_MS),
                        )
                    )
                } else {
                    motions.add(
                        DumbGestureFingerMotion.Move(
                            id = nextMotionId(motions),
                            position = Point(point.x.roundToInt(), point.y.roundToInt()),
                            durationMs = durationMs.coerceAtMost(MAX_GESTURE_DURATION_MS),
                        )
                    )
                }
            }

        motions.add(
            DumbGestureFingerMotion.Release(id = nextMotionId(motions))
        )

        return DumbGestureFinger(
            id = Identifier(id, asTemporary = true),
            startTimeMs = (startTimeMs - gestureStartTimeMs).coerceAtLeast(0L),
            motions = motions,
        )
    }

    private fun isStill(from: RecordedPoint, to: RecordedPoint): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        return sqrt(dx * dx + dy * dy) < STILL_DISTANCE_PX
    }

    private fun nextMotionId(motions: List<DumbGestureFingerMotion>): Identifier =
        Identifier(motions.size.toLong() + 1, asTemporary = true)

    /** Android allows a gesture to last at most 59 999 ms. */
    private const val MAX_GESTURE_DURATION_MS = 59_000L
    private const val MAX_PRESS_DURATION_MS = 10_000L
    private const val MIN_MOTION_DURATION_MS = 1L
    private const val STILL_DISTANCE_PX = 1f

    private const val TEMP_SCENARIO_ID = 1L
}
