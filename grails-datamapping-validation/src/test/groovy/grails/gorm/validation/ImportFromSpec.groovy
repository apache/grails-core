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
package grails.gorm.validation

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.datastore.mapping.validation.ValidatorRegistry
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.persistence.Entity

class ImportFromSpec extends Specification {

    void "test gets the metaConstraints"() {
        given:"setup validator registry"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        def entity = mappingContext.addPersistentEntity(ImportFromTestEntity)
        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())

        expect:"The validator is correct"
        def validator = (PersistentEntityValidator)registry.getValidator(entity)
        validator.constrainedProperties['createdDay'].metaConstraints["bindable"] == false
        validator.constrainedProperties['createdDay'].metaConstraints["example"] == "2017-12-31"
    }

    @Unroll
    void 'an entity importing from an entity gets the configured nullable default (nullable = #defaultNullable)'() {
        given: 'a validator registry with the nullable default set'
        var mappingContext = new KeyValueMappingContext('test')
        mappingContext.addPersistentEntities(ImportFromSourceEntity, ImportFromTargetEntity)
        var settings = new ConnectionSourceSettings()
        settings.default.nullable = defaultNullable
        var registry = new DefaultValidatorRegistry(mappingContext, settings)

        and: 'a target entity with every property left null'
        var target = new ImportFromTargetEntity()
        var errors = new ValidationErrors(target)

        when:
        registry.getValidator(mappingContext.getPersistentEntity(ImportFromTargetEntity.name)).validate(target, errors)

        then: 'the property the source leaves unconstrained follows the configured default'
        errors.hasFieldErrors('name') == !defaultNullable

        and: 'the constraints the source declares are imported as they are'
        errors.getFieldError('code')?.code == 'nullable'
        !errors.hasFieldErrors('description')

        where:
        defaultNullable << [false, true]
    }

    @Unroll
    void 'a command object importing from an entity gets the configured nullable default (nullable = #defaultNullable)'() {
        given: 'a validator registry with the nullable default set'
        var mappingContext = new KeyValueMappingContext('test')
        mappingContext.addPersistentEntity(ImportFromSourceEntity)
        var settings = new ConnectionSourceSettings()
        settings.default.nullable = defaultNullable
        var registry = new DefaultValidatorRegistry(mappingContext, settings)

        when: 'the command object is evaluated as Validateable evaluates it, required by default'
        var constraints = registry.evaluate(ImportFromCommand, false)

        then: 'the property the source leaves unconstrained follows the configured default'
        constraints.name.nullable == defaultNullable

        and: 'the constraints the source declares are imported as they are'
        !constraints.code.nullable
        constraints.description.nullable

        and: 'the command object property that is not imported keeps the command object default'
        !constraints.notImported.nullable

        where:
        defaultNullable << [false, true]
    }
}

@Entity
class ImportFromSourceEntity {

    String name
    String code
    String description

    static constraints = {
        code(nullable: false)
        description(nullable: true)
    }
}

@Entity
class ImportFromTargetEntity {

    String name
    String code
    String description

    static constraints = {
        importFrom(ImportFromSourceEntity)
    }
}

class ImportFromCommand {

    String name
    String code
    String description
    String notImported

    static constraints = {
        importFrom(ImportFromSourceEntity)
    }
}

@Entity
class ImportFromTestEntity implements DayStamp {
    String name

    static constraints = {
        importFrom(DayStampConstraints)
    }
}

@CompileStatic
trait DayStamp {

    Date createdDay

}

class DayStampConstraints implements DayStamp {

    static constraints = {
        createdDay nullable: true, bindable: false, example: "2017-12-31"
    }
}



