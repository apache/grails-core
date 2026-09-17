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
package org.grails.datastore.gorm.validation.registry.support

import org.springframework.context.support.StaticMessageSource

import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.validation.ValidatorRegistry
import spock.lang.Specification

class ValidatorRegistriesSpec extends Specification {

    MappingContext mappingContext = new SimpleMapDatastore().mappingContext
    ConnectionSourceSettings settings = new ConnectionSourceSettings()

    void "test isJakartaValidationAvailable reflects whether jakarta.validation.Validation is on the classpath"() {
        expect:
        ValidatorRegistries.isJakartaValidationAvailable() == ClassUtils.isPresent('jakarta.validation.Validation')
    }

    void "test createValidatorRegistry with a default message source picks the registry matching classpath availability"() {
        when:
        ValidatorRegistry registry = ValidatorRegistries.createValidatorRegistry(mappingContext, settings)

        then:
        ValidatorRegistries.isJakartaValidationAvailable() ?
                registry instanceof JakartaValidatorRegistry :
                registry instanceof DefaultValidatorRegistry
    }

    void "test createValidatorRegistry with an explicit message source picks the registry matching classpath availability"() {
        given:
        def messageSource = new StaticMessageSource()

        when:
        ValidatorRegistry registry = ValidatorRegistries.createValidatorRegistry(mappingContext, settings, messageSource)

        then:
        ValidatorRegistries.isJakartaValidationAvailable() ?
                registry instanceof JakartaValidatorRegistry :
                registry instanceof DefaultValidatorRegistry
    }
}
