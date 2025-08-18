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

package org.grails.forge.feature.config

import org.grails.forge.BeanContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.Options
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

class PropertiesSpec extends BeanContextSpec implements CommandOutputFixture {

    @Shared
    @Subject
    Properties properties = beanContext.getBean(Properties)

    void "order is highest"() {
        expect:
        properties.order == FeaturePhase.HIGHEST.getOrder()
    }

    @Unroll
    void "properties supports #description application type"(ApplicationType applicationType, String description) {
        expect:
        properties.supports(applicationType)

        where:
        applicationType << ApplicationType.values()
        description = applicationType.name
    }

    void "test configuration files generated for properties feature"() {
        when:
        GeneratorContext generatorContext = buildGeneratorContext(['properties'], { context ->
            context.getBootstrapConfiguration().put("abc", 123)
            context.getConfiguration("test", ApplicationConfiguration.testConfig()).put("abc", 456)
            context.getConfiguration("prod", new ApplicationConfiguration("prod")).put("abc", 789)
        }, new Options())
        def output = generate(ApplicationType.WEB, generatorContext)

        then:
        output["grails-app/conf/application.properties"].readLines().find {it == 'info.app.name=@info.app.name@' }
        output["grails-app/conf/bootstrap.properties"].readLines().find { it == 'abc=123' }
        output["src/test/resources/application-test.properties"].readLines().find { it == 'abc=456' }
        output["grails-app/conf/application-prod.properties"].readLines().find { it == 'abc=789' }
    }
}
