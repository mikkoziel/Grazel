/*
 * Copyright 2022 Grabtaxi Holdings PTE LTD (GRAB)
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

package com.grab.grazel.util

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

fun <T : Task> TaskProvider<out T>.dependsOn(tasks: Collection<TaskProvider<out Task>>): TaskProvider<out T> {
    if (tasks.isEmpty().not()) {
        configure { dependsOn(tasks) }
    }
    return this
}

fun <T : Task> TaskProvider<out T>.dependsOn(vararg tasks: TaskProvider<out Task>): TaskProvider<out T> {
    if (tasks.isEmpty().not()) {
        configure { dependsOn(*tasks) }
    }
    return this
}