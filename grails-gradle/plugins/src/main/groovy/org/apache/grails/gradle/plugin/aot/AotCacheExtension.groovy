/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.gradle.plugin.aot

import groovy.transform.CompileStatic
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * What an application says about the cache the JDK writes for it.
 *
 * <p>A cache makes the next start fast at whatever the training run did, so the only thing an
 * application really has to say is which of its pages matter. The rest has an answer that is right
 * far more often than not.</p>
 *
 * <pre>
 * grails {
 *     aotCache {
 *         enabled = true
 *         paths = ['/', '/login', '/user/index']
 *     }
 * }
 * </pre>
 *
 * @since 8.0
 */
@CompileStatic
abstract class AotCacheExtension {

    /** Off unless asked for: training runs the application, which is not part of an ordinary build. */
    abstract Property<Boolean> getEnabled()

    /**
     * The paths asked for while training. Empty trains the start alone, which is what
     * {@code spring.context.exit=onRefresh} records and is worth having on its own -- but leaves
     * every request path to be worked out on the day.
     */
    abstract ListProperty<String> getPaths()

    /**
     * Given to the training run, and to be given to every run that reads the cache. A cache records
     * what it saw, so a run configured differently from the training run reads a cache of a
     * different application.
     */
    abstract ListProperty<String> getJvmArguments()

    /** Where the training run listens. Not the port the application is deployed on. */
    abstract Property<Integer> getPort()

    abstract Property<Integer> getStartTimeoutSeconds()
}
