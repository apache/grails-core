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

package org.grails.web.commandobjects

import grails.testing.web.GrailsWebUnitTest
import org.grails.validation.ConstraintEvalUtils
import spock.lang.Specification

/**
 * Tests for command object validation without DataTest trait.
 * This spec modifies global shared constraints via doWithConfig() which affects
 * ConstraintEvalUtils.defaultConstraintsMap - a static cache shared across all tests
 * in the same JVM fork. The setup/cleanup methods clear this cache to prevent test environment pollution.
 */
class CommandObjectNoDataSpec extends Specification implements GrailsWebUnitTest {

    Closure doWithConfig() {{ config ->
        config['grails.gorm.default.constraints'] = {
            isProg inList: ['Emerson', 'Lake', 'Palmer']
        }
    }}

    /**
     * Clear the static constraints cache for Artist class.
     * This prevents test environment pollution because the Validateable trait caches
     * constraints in a static field, and constraints may be evaluated before doWithConfig()
     * has registered the shared constraint 'isProg'.
     *
     * Also clear ConstraintEvalUtils.defaultConstraintsMap which caches shared constraints
     * globally. Without this cleanup, another test's config may have been cached,
     * causing the 'isProg' shared constraint to not be found.
     */
    def setup() {
        ConstraintEvalUtils.clearDefaultConstraints()
        Artist.clearConstraintsMapCache()
    }

    def cleanup() {
        ConstraintEvalUtils.clearDefaultConstraints()
        Artist.clearConstraintsMapCache()
    }

    void "test shared constraint"() {
        when:
        Artist artist = new Artist(name: "X")

        then:
        !artist.validate()
        artist.errors['name'].code == 'not.inList'
    }
}
