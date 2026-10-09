/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.buildsrc

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.ResolutionStrategy

/**
 * Fixed releases are not governed by these knobs. Dynamic listings are release metadata,
 * so they stay at Gradle's 24 hour default. Changing modules (snapshots) are polled
 * immediately on ordinary CI and within 10 minutes locally. A reproducible build must
 * not re-resolve them, even when CI is also set.
 */
@CompileStatic
class DependencyCachePolicy {

    static void apply(ResolutionStrategy strategy, boolean ci, boolean reproducible) {
        strategy.cacheDynamicVersionsFor(24, 'hours')
        if (reproducible) {
            strategy.cacheChangingModulesFor(24, 'hours')
        } else if (ci) {
            strategy.cacheChangingModulesFor(0, 'hours')
        } else {
            strategy.cacheChangingModulesFor(10, 'minutes')
        }
    }
}
