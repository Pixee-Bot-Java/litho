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

import com.facebook.litho.config.ComponentsConfiguration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Thread pool executor implementation used to calculate layout on multiple background threads. */
class LayoutThreadPoolExecutor
@JvmOverloads
constructor(
    corePoolSize: Int,
    maxPoolSize: Int,
    priority: Int,
    layoutThreadInitializer: Runnable? = null
) :
    ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        ComponentsConfiguration.layoutThreadKeepAliveTimeMs,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        LayoutThreadFactory(priority, layoutThreadInitializer)) {
  init {
    allowCoreThreadTimeOut(ComponentsConfiguration.shouldAllowCoreThreadTimeout)
  }
}
