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

package grails.test

import grails.util.Environment
import spock.lang.Shared
import spock.lang.Specification

/**
 * Grails internal class for running Spec in a defined Grails Environment
 *
 * returns Grails Environment (System Properties) back to original after running the Spec.
 *
 * For example
 * String grailsEnvName = "test"
 * runs the test in "test" Grails Environment
 *
 * @author Lari Hotari
 */
abstract class AbstractGrailsEnvChangingSpec extends Specification {
    @Shared
    String originalGrailsEnv
    @Shared
    String originalGrailsEnvDefault
    static List<String> grailsEnvs = [Environment.DEVELOPMENT.name, Environment.TEST.name, Environment.PRODUCTION.name]

    String getGrailsEnvName() {
        null
    }

    def setup() {
        // set grails.env before each test
        changeGrailsEnv(grailsEnvName)
    }

    def setupSpec() {
        // save grails.env and grails.env.default keys before running this spec
        originalGrailsEnv = System.getProperty(Environment.KEY)
        originalGrailsEnvDefault = System.getProperty(Environment.DEFAULT)
    }

    def cleanupSpec() {
        // reset grails.env and grails.env.default keys after running this spec
        resetGrailsEnvironment()
    }

    protected void changeGrailsEnv(String newEnv) {
        resetGrailsEnvironment()
        if (newEnv != null) {
            System.setProperty(Environment.KEY, newEnv)
        }
    }

    protected void resetGrailsEnvironment() {
        if (originalGrailsEnv != null) {
            System.setProperty(Environment.KEY, originalGrailsEnv)
        } else {
            System.clearProperty(Environment.KEY)
        }

        if (originalGrailsEnvDefault != null) {
            System.setProperty(Environment.DEFAULT, originalGrailsEnvDefault)
        } else {
            System.clearProperty(Environment.DEFAULT)
        }
    }

    protected createCombinationsForGrailsEnvs(params) {
        [params,grailsEnvs].combinations().collect { it.flatten() }
    }
}
