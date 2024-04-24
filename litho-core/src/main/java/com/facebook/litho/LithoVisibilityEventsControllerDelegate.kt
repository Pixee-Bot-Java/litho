/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho

import androidx.annotation.IntDef
import androidx.annotation.UiThread
import com.facebook.litho.LithoVisibilityEventsController.LithoVisibilityState
import java.lang.IllegalStateException
import java.util.ArrayList

/**
 * Default [LithoVisibilityEventsController] implementation. Defines the standard state changes for
 * a Litho ComponentTree. A custom LithoVisibilityEventsController implementation can change the
 * lifecycle state but delegate to this to handle the effects of the state change. See an example of
 * how this facilitates a custom lifecycle implementation in [AOSPLithoVisibilityEventsController].
 */
class LithoVisibilityEventsControllerDelegate : LithoVisibilityEventsController {

  private val lithoLifecycleListeners: MutableSet<LithoLifecycleListener> = HashSet()
  override var lifecycleStatus = LithoVisibilityState.HINT_VISIBLE
    private set

  @IntDef(
      LifecycleTransitionStatus.VALID,
      LifecycleTransitionStatus.NO_OP,
      LifecycleTransitionStatus.INVALID)
  annotation class LifecycleTransitionStatus {
    companion object {
      const val VALID = 0
      const val NO_OP = 1
      const val INVALID = 2
    }
  }

  @UiThread
  override fun moveToVisibilityState(newLifecycleState: LithoVisibilityState) {
    ThreadUtils.assertMainThread()
    if (newLifecycleState === LithoVisibilityState.DESTROYED &&
        lifecycleStatus === LithoVisibilityState.HINT_VISIBLE) {
      moveToVisibilityState(LithoVisibilityState.HINT_INVISIBLE)
    }
    @LifecycleTransitionStatus
    val transitionStatus = getTransitionStatus(lifecycleStatus, newLifecycleState)
    if (transitionStatus == LifecycleTransitionStatus.INVALID) {
      ComponentsReporter.emitMessage(
          ComponentsReporter.LogLevel.WARNING,
          "LithoVisibilityEventsController",
          "Cannot move from state $lifecycleStatus to state $newLifecycleState")
      return
    }
    if (transitionStatus == LifecycleTransitionStatus.VALID) {
      lifecycleStatus = newLifecycleState
      when (newLifecycleState) {
        LithoVisibilityState.HINT_VISIBLE -> {
          notifyOnResumeVisible()
          return
        }
        LithoVisibilityState.HINT_INVISIBLE -> {
          notifyOnPauseVisible()
          return
        }
        LithoVisibilityState.DESTROYED -> {
          notifyOnDestroy()
          return
        }
        else -> throw IllegalStateException("State not known")
      }
    }
  }

  @Synchronized
  override fun addListener(listener: LithoLifecycleListener) {
    lithoLifecycleListeners.add(listener)
  }

  @Synchronized
  override fun removeListener(listener: LithoLifecycleListener) {
    lithoLifecycleListeners.remove(listener)
  }

  private fun notifyOnResumeVisible() {
    val lithoLifecycleListeners: List<LithoLifecycleListener>
    synchronized(this) { lithoLifecycleListeners = ArrayList(this.lithoLifecycleListeners) }
    for (lithoLifecycleListener in lithoLifecycleListeners) {
      lithoLifecycleListener.onMovedToState(LithoVisibilityState.HINT_VISIBLE)
    }
  }

  private fun notifyOnPauseVisible() {
    val lithoLifecycleListeners: List<LithoLifecycleListener>
    synchronized(this) { lithoLifecycleListeners = ArrayList(this.lithoLifecycleListeners) }
    for (lithoLifecycleListener in lithoLifecycleListeners) {
      lithoLifecycleListener.onMovedToState(LithoVisibilityState.HINT_INVISIBLE)
    }
  }

  private fun notifyOnDestroy() {
    val lithoLifecycleListeners: List<LithoLifecycleListener>
    synchronized(this) { lithoLifecycleListeners = ArrayList(this.lithoLifecycleListeners) }
    for (lithoLifecycleListener in lithoLifecycleListeners) {
      lithoLifecycleListener.onMovedToState(LithoVisibilityState.DESTROYED)
    }
  }

  companion object {
    @LifecycleTransitionStatus
    private fun getTransitionStatus(
        currentState: LithoVisibilityState,
        nextState: LithoVisibilityState
    ): Int {
      if (currentState === LithoVisibilityState.DESTROYED) {
        return LifecycleTransitionStatus.INVALID
      }
      if (nextState === LithoVisibilityState.DESTROYED) {
        // You have to move through HINT_INVISIBLE before moving to DESTROYED.
        return if (currentState === LithoVisibilityState.HINT_INVISIBLE)
            LifecycleTransitionStatus.VALID
        else LifecycleTransitionStatus.INVALID
      }
      if (nextState === LithoVisibilityState.HINT_VISIBLE) {
        if (currentState === LithoVisibilityState.HINT_VISIBLE) {
          return LifecycleTransitionStatus.NO_OP
        }
        return if (currentState === LithoVisibilityState.HINT_INVISIBLE) {
          LifecycleTransitionStatus.VALID
        } else {
          LifecycleTransitionStatus.INVALID
        }
      }
      if (nextState === LithoVisibilityState.HINT_INVISIBLE) {
        if (currentState === LithoVisibilityState.HINT_INVISIBLE) {
          return LifecycleTransitionStatus.NO_OP
        }
        return if (currentState === LithoVisibilityState.HINT_VISIBLE) {
          LifecycleTransitionStatus.VALID
        } else {
          LifecycleTransitionStatus.INVALID
        }
      }
      return LifecycleTransitionStatus.INVALID
    }
  }
}
