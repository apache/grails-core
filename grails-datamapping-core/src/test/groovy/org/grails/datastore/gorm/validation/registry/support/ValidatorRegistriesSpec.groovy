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

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification

class ValidatorRegistriesSpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext("test")

    void setup() {
        mappingContext.addPersistentEntities(RegistryBook)
        mappingContext.initialize()
    }

    void "reports that jakarta.validation is available on the classpath"() {
        expect:
        ValidatorRegistries.isJakartaValidationAvailable()
    }

    void "creates a Jakarta backed registry with a default static message source"() {
        when:
        ValidatorRegistry registry = ValidatorRegistries.createValidatorRegistry(mappingContext, new ConnectionSourceSettings())

        then:
        registry instanceof JakartaValidatorRegistry
    }

    void "creates a registry using the supplied message source"() {
        given:
        def messageSource = new StaticMessageSource()

        when:
        def registry = (JakartaValidatorRegistry) ValidatorRegistries.createValidatorRegistry(mappingContext, new ConnectionSourceSettings(), messageSource)

        then:
        registry.messageSource.is(messageSource)
    }
}

@Entity
class RegistryBook {
    String title
}
