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

package org.grails.forge.feature

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.fixture.ContextFixture
import org.grails.forge.fixture.ProjectFixture
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.Options
import spock.lang.Unroll

class FeatureOrderSpec extends ApplicationContextSpec implements ContextFixture, ProjectFixture, CommandOutputFixture {

    @Unroll
    void "the features of a #applicationType application with #selected selected are ordered by their order and then by name"() {
        given:
        FeatureContext featureContext = buildFeatureContext(selected, new Options(), applicationType)
        featureContext.processSelectedFeatures()

        when:
        List<Feature> features = featureContext.getFinalFeatures(ConsoleOutput.NOOP).toList()

        then: 'there are features that share an order, so the name decides'
        features.size() > features*.order.unique().size()

        and:
        features == features.toSorted { Feature a, Feature b -> a.order <=> b.order ?: a.name <=> b.name }

        where:
        applicationType          | selected
        ApplicationType.WEB      | []
        ApplicationType.WEB      | ['database-migration', 'grails-quartz', 'cache-ehcache']
        ApplicationType.REST_API | []
        ApplicationType.PLUGIN   | []
    }

    void "the feature names are listed in the same order however the features were selected"() {
        expect:
        getFeatures(['grails-quartz', 'database-migration', 'cache-ehcache']).toList() ==
                getFeatures(['cache-ehcache', 'database-migration', 'grails-quartz']).toList()
    }

    void "the generated application is the same however the features were selected"() {
        when:
        Map<String, String> first = generate(['grails-quartz', 'database-migration', 'cache-ehcache', 'asciidoctor'])
        Map<String, String> second = generate(['asciidoctor', 'cache-ehcache', 'database-migration', 'grails-quartz'])

        then:
        first.keySet().toList() == second.keySet().toList()
        first == second
    }
}
