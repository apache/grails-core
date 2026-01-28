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

    // Cache the static field helper interface for performance
    private static final Class<?> STATIC_FIELD_HELPER = Class.forName('grails.validation.Validateable$Trait$StaticFieldHelper')

    Closure doWithConfig() {{ config ->
        config['grails.gorm.default.constraints'] = {
            isProg inList: ['Emerson', 'Lake', 'Palmer']
        }
    }}

    /**
     * Clear the static constraints cache for Artist class.
     * This is necessary because the Validateable trait caches constraints in a static field,
     * and when tests run sequentially within a fork, the constraints may be evaluated before
     * doWithConfig() has registered the shared constraint 'isProg'.
     *
     * Also clear ConstraintEvalUtils.defaultConstraintsMap which caches shared constraints
     * globally. When tests run sequentially, another test's config may have been cached,
     * causing the 'isProg' shared constraint to not be found.
     *
     * IMPORTANT: We access 'config' first to ensure GrailsApplication is initialized
     * and Holders.grailsApplication is set. This triggers doWithConfig() which registers
     * the 'isProg' shared constraint. We then clear the caches so they will be repopulated
     * from the freshly-configured application when validate() is called.
     */
    def setup() {
        // Access config to ensure grailsApplication is initialized and Holders is populated
        // This triggers doWithConfig() which registers the 'isProg' shared constraint
        assert config != null
        
        // Now clear the caches so they will be repopulated from the fresh config
        ConstraintEvalUtils.clearDefaultConstraints()
        clearConstraintsMapCache(Artist)
    }

    def cleanup() {
        ConstraintEvalUtils.clearDefaultConstraints()
        clearConstraintsMapCache(Artist)
    }

    /**
     * Clears the private static constraintsMapInternal field in the Validateable trait.
     * In Groovy 4, static fields in traits are accessed via the Validateable$Trait$StaticFieldHelper
     * interface which implementing classes implement. This method uses that interface to clear the cache.
     * This is used for test isolation until a public API is available in Grails 7.1.
     */
    private static void clearConstraintsMapCache(Class<?> clazz) {
        // In Groovy 4, classes implementing a trait with static fields also implement
        // the TraitName$Trait$StaticFieldHelper interface with getter/setter methods
        if (STATIC_FIELD_HELPER.isAssignableFrom(clazz)) {
            // The setter method name follows the pattern: traitFQN__fieldName$set
            def setterMethod = clazz.getMethod('grails_validation_Validateable__constraintsMapInternal$set', Map)
            setterMethod.invoke(null, (Map) null)
        }
    }

    void "test shared constraint"() {
        when:
        Artist artist = new Artist(name: "X")

        then:
        !artist.validate()
        artist.errors['name'].code == 'not.inList'
    }
}
