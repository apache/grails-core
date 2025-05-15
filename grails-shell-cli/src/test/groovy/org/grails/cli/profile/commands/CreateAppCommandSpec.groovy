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
package org.grails.cli.profile.commands

import grails.build.logging.GrailsConsole
import org.grails.cli.profile.Feature
import org.grails.cli.profile.Profile
import org.spockframework.util.StringMessagePrintStream
import spock.lang.PendingFeatureIf
import spock.lang.Shared
import spock.lang.Specification
/**
 * Created by Jim on 7/18/2016.
 */
class CreateAppCommandSpec extends Specification {

    @Shared
    StringPrintStream sps

    PrintStream originalOut

    void setup() {
        System.setProperty("org.fusesource.jansi.Ansi.disable", "true")
        originalOut = GrailsConsole.instance.out
        sps = new StringPrintStream()
        GrailsConsole.instance.out = sps
    }

    void cleanup() {
        System.setProperty("org.fusesource.jansi.Ansi.disable", "false")
        GrailsConsole.instance.out = originalOut
    }

    @PendingFeatureIf({
        // Mock() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void "test evaluateFeatures - multiple, some valid"() {
        given:
        Feature bar = Mock(Feature) {
            2 * getName() >> "bar"
        }
        Profile profile = Mock(Profile) {
            1 * getName() >> "web"
            2 * getFeatures() >> [bar]
            1 * getRequiredFeatures() >> []
        }

        when:
        Iterable<Feature> features = new CreateAppCommand().evaluateFeatures(profile, ['foo', 'bar'])

        then:
        features.size() == 1
        features[0] == bar
        sps.toString() == "Warning |\nFeature foo does not exist in the profile web!\n"
    }

    @PendingFeatureIf({
        // Mock() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void "test evaluateFeatures - multiple, all valid"() {
        given:
        Feature foo = Mock(Feature) {
            2 * getName() >> "foo"
        }
        Feature bar = Mock(Feature) {
            2 * getName() >> "bar"
        }
        Profile profile = Mock(Profile) {
            0 * getName()
            2 * getFeatures() >> [foo, bar]
            1 * getRequiredFeatures() >> []
        }

        when:
        Iterable<Feature> features = new CreateAppCommand().evaluateFeatures(profile, ['foo', 'bar'])

        then:
        features.size() == 2
        features[0] == foo
        features[1] == bar
        sps.toString() == ""
    }

    @PendingFeatureIf({
        // Mock() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void "test evaluateFeatures fat finger"() {
        given:
        Feature bar = Mock(Feature) {
            2 * getName() >> "mongodb"
        }
        Profile profile = Mock(Profile) {
            1 * getName() >> "web"
            2 * getFeatures() >> [bar]
            1 * getRequiredFeatures() >> []
        }

        when:
        Iterable<Feature> features = new CreateAppCommand().evaluateFeatures(profile, ['mongo'])

        then:
        features.size() == 0
        sps.toString() == "Warning |\nFeature mongo does not exist in the profile web! Possible solutions: mongodb\n"
    }

    class StringPrintStream extends StringMessagePrintStream {
        StringBuilder stringBuilder = new StringBuilder()
        @Override
        protected void printed(String message) {
            stringBuilder.append(message)
        }

        String toString() {
            stringBuilder.toString()
        }
    }
}
