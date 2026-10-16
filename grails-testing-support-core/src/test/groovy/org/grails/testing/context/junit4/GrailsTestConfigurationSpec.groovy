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
package org.grails.testing.context.junit4

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.springframework.context.ApplicationContextInitializer
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import grails.boot.test.GrailsApplicationContextLoader

class GrailsTestConfigurationSpec extends Specification {

    void 'the annotation is a runtime type annotation backed by the grails context loader'() {
        expect:
        GrailsTestConfiguration.isAnnotation()
        GrailsTestConfiguration.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        GrailsTestConfiguration.getAnnotation(Target).value() == [ElementType.TYPE] as ElementType[]
        GrailsTestConfiguration.isAnnotationPresent(Inherited)
        GrailsTestConfiguration.isAnnotationPresent(Documented)
        GrailsTestConfiguration.getAnnotation(ContextConfiguration).loader() == GrailsApplicationContextLoader
    }

    void 'the members default to the empty context configuration'() {
        expect:
        GrailsTestConfiguration.getDeclaredMethod('locations').defaultValue == new String[0]
        GrailsTestConfiguration.getDeclaredMethod('classes').defaultValue == new Class[0]
        GrailsTestConfiguration.getDeclaredMethod('initializers').defaultValue == new Class[0]
        GrailsTestConfiguration.getDeclaredMethod('inheritLocations').defaultValue == true
        GrailsTestConfiguration.getDeclaredMethod('inheritInitializers').defaultValue == true
        GrailsTestConfiguration.getDeclaredMethod('name').defaultValue == ''
    }

    void 'the annotation values can be read from an annotated class'() {
        when:
        GrailsTestConfiguration config = Configured.getAnnotation(GrailsTestConfiguration)
        GrailsTestConfiguration inherited = InheritsConfigured.getAnnotation(GrailsTestConfiguration)

        then:
        config.locations() == ['a.xml'] as String[]
        config.classes() == [String] as Class[]
        config.initializers() == [CueInitializer] as Class[]
        !config.inheritLocations()
        config.inheritInitializers()
        config.name() == 'named'
        inherited.is(config) || inherited.name() == 'named'
    }

}

class CueInitializer implements ApplicationContextInitializer {

    @Override
    void initialize(org.springframework.context.ConfigurableApplicationContext applicationContext) {
    }

}

@GrailsTestConfiguration(locations = 'a.xml', classes = String, initializers = CueInitializer, inheritLocations = false, name = 'named')
class Configured {
}

class InheritsConfigured extends Configured {
}
