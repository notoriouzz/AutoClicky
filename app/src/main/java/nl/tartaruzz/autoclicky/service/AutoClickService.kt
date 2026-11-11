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

package nl.tartaruzz.autoclicky.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor

import dagger.hilt.android.AndroidEntryPoint

import nl.tartaruzz.autoclicky.engine.EngineController
import nl.tartaruzz.autoclicky.recording.RecordingController

import javax.inject.Inject

/**
 * Accessibility service used to inject the clicks and gestures of the vendored Smart AutoClicker (Klick'r) engine.
 *
 * The Android framework starts it once the user enabled it in the system accessibility settings, and keeps it alive
 * afterwards. It is the only place initializing [AndroidActionExecutor]: no gesture can be dispatched before
 * [onServiceConnected] has been called. It also hosts the recording overlays, which need an accessibility service to be
 * displayed without any extra permission.
 */
@AndroidEntryPoint
class AutoClickService : AccessibilityService() {

    @Inject lateinit var androidActionExecutor: AndroidActionExecutor
    @Inject lateinit var engineController: EngineController
    @Inject lateinit var recordingController: RecordingController

    override fun onServiceConnected() {
        super.onServiceConnected()

        androidActionExecutor.init(this)
        recordingController.attach(this)
        engineController.onAccessibilityServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        recordingController.detach()
        engineController.onAccessibilityServiceDisconnected()
        androidActionExecutor.clear()

        return super.onUnbind(intent)
    }
}
