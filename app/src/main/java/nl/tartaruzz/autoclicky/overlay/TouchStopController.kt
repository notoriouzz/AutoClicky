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

package nl.tartaruzz.autoclicky.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import nl.tartaruzz.autoclicky.engine.EngineController

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops a playing template as soon as the user touches the screen.
 *
 * Touching the screen is the natural way to interrupt a playback: while a template runs the floating panel is hidden, so
 * a touch is the only way to stop it and can land anywhere on the screen.
 *
 * The touch is observed with the same technique as the recorder: a one pixel, non touchable window flagged with
 * `FLAG_WATCH_OUTSIDE_TOUCH` receives an `ACTION_OUTSIDE` event for every gesture made outside of it, while the app
 * under the finger still receives the touch. Two kinds of events are ignored:
 * - the gestures injected by the engine itself, which have no tool type (real touches always have one),
 * - the touches on the AutoClicky windows (panel and cards), which the system does not report to a window of the same
 *   app, so pressing a panel button never stops the run by itself.
 *
 * The screen turning off also stops the playback: it is observed through a receiver on `ACTION_SCREEN_OFF`, which covers
 * a power press that switches the screen off and the automatic lock. The other physical buttons (volume, power while
 * the screen stays on) are not visible to an accessibility service, see the documentation.
 */
@Singleton
class TouchStopController @Inject constructor(
    private val engineController: EngineController,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var service: AccessibilityService? = null
    private var windowManager: WindowManager? = null
    private var watcherView: View? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var observerJob: Job? = null

    fun attach(service: AccessibilityService) {
        this.service = service
        windowManager = service.getSystemService(WindowManager::class.java)
        observeEngine()
    }

    fun detach() {
        observerJob?.cancel()
        observerJob = null
        stopWatching()
        windowManager = null
        service = null
    }

    private fun observeEngine() {
        observerJob?.cancel()
        observerJob = scope.launch {
            engineController.isRunning.collect { isRunning ->
                if (isRunning) startWatching() else stopWatching()
            }
        }
    }

    private fun startWatching(): Unit = runOnMainThread {
        if (watcherView != null) return@runOnMainThread

        val service = service ?: return@runOnMainThread

        @SuppressLint("ClickableViewAccessibility") // The view only receives the touches made outside of it.
        val view = View(service).apply {
            setOnTouchListener { _, event ->
                onWatcherTouchEvent(event)
                false
            }
        }

        if (!tryAddView(view, watcherLayoutParams())) {
            Log.w(TAG, "Can't watch the user touches, the watcher window was refused")
            return@runOnMainThread
        }

        watcherView = view

        if (screenOffReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(TAG, "Screen turned off, stopping the running template")
                    engineController.stopScenario()
                }
            }
            service.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_EXPORTED)
            screenOffReceiver = receiver
        }

        Log.d(TAG, "Watching for a user touch to stop the running template")
    }

    private fun stopWatching(): Unit = runOnMainThread {
        screenOffReceiver?.let { receiver ->
            screenOffReceiver = null
            try {
                service?.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // The receiver was already unregistered.
            }
        }

        val view = watcherView ?: return@runOnMainThread
        watcherView = null

        try {
            windowManager?.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }

        Log.d(TAG, "Stopped watching the user touches")
    }

    private fun onWatcherTouchEvent(event: MotionEvent) {
        if (!event.isFromUser()) return

        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            Log.d(TAG, "User touched the screen, stopping the running template")
            engineController.stopScenario()
        }
    }

    private fun tryAddView(view: View, params: WindowManager.LayoutParams): Boolean =
        try {
            windowManager?.addView(view, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun watcherLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WATCHER_WINDOW_SIZE_PX,
            WATCHER_WINDOW_SIZE_PX,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private companion object {
        const val WATCHER_WINDOW_SIZE_PX = 1
        const val TAG = "TouchStopController"
    }
}

/**
 * Gestures injected by an accessibility service always have an unknown tool type, while real touches have one (finger,
 * stylus...). This is the same check the vendored `UserTouchWatcher` does.
 */
private fun MotionEvent.isFromUser(): Boolean =
    pointerCount > 0 && getToolType(0) != MotionEvent.TOOL_TYPE_UNKNOWN
