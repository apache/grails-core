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

import org.gradle.api.artifacts.ResolutionStrategy
import spock.lang.Specification

class DependencyCachePolicySpec extends Specification {

    def "dynamic release listings stay cached for 24 hours and snapshots follow the build kind"() {
        given:
        ResolutionStrategy strategy = Mock(ResolutionStrategy)

        when:
        DependencyCachePolicy.apply(strategy, ci, reproducible)

        then:
        1 * strategy.cacheDynamicVersionsFor(24, 'hours')
        1 * strategy.cacheChangingModulesFor(changingValue, changingUnits)

        where:
        ci    | reproducible || changingValue | changingUnits
        false | false        || 10            | 'minutes'
        true  | false        || 0             | 'hours'
        false | true         || 24            | 'hours'
        true  | true         || 24            | 'hours'
    }
}
