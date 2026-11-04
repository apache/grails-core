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
package org.grails.datastore.gorm.validation.jakarta

import jakarta.validation.constraints.NotBlank

import org.springframework.validation.Validator as SpringValidator
import org.springframework.validation.annotation.Validated

import grails.gorm.annotation.Entity
import grails.gorm.validation.PersistentEntityValidator
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class JakartaValidatorRegistrySpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext("test")
    JakartaValidatorRegistry registry

    void setup() {
        mappingContext.addPersistentEntities(PlainBook, ValidatedBook)
        mappingContext.initialize()
        registry = new JakartaValidatorRegistry(mappingContext, new ConnectionSourceSettings())
    }

    void cleanup() {
        registry.close()
    }

    void "isAvailable reports that jakarta.validation is on the classpath"() {
        expect:
        JakartaValidatorRegistry.isAvailable()
    }

    void "returns a jakarta backed validator for entities annotated with @Validated"() {
        given:
        def entity = mappingContext.getPersistentEntity(ValidatedBook.name)

        when:
        SpringValidator validator = registry.getValidator(entity)

        then:
        validator instanceof GormValidatorAdapter
    }

    void "falls back to the default constraint based validator for entities without @Validated"() {
        given:
        def entity = mappingContext.getPersistentEntity(PlainBook.name)

        when:
        SpringValidator validator = registry.getValidator(entity)

        then:
        validator instanceof PersistentEntityValidator
    }

    void "exposes the underlying jakarta ValidatorFactory operations"() {
        expect:
        registry.validator != null
        registry.usingContext() != null
        registry.messageInterpolator != null
        registry.traversableResolver != null
        registry.constraintValidatorFactory != null
        registry.parameterNameProvider != null
        registry.clockProvider != null
    }

}

@Entity
class PlainBook {
    String title
}

@Entity
@Validated
class ValidatedBook {
    @NotBlank
    String title
}
