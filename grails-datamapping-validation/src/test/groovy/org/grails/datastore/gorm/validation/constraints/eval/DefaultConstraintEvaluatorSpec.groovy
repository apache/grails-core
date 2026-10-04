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
package org.grails.datastore.gorm.validation.constraints.eval

import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.persistence.Entity

class DefaultConstraintEvaluatorSpec extends Specification {

    void "test importFrom honors default nullable"() {
        given:
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()), new KeyValueMappingContext("test"), Collections.emptyMap())

        when:
        def constraints = evaluator.evaluate(Constraints, true)

        then:
        constraints.name.isNullable()
        !constraints.name.isBlank()

        when:
        constraints = evaluator.evaluate(MoreConstraints, true)

        then:
        constraints.name.isNullable()
        !constraints.name.isBlank()

        when:
        constraints = evaluator.evaluate(MoreConstraints, false)

        then:
        !constraints.name.isNullable()
        !constraints.name.isBlank()
    }

    @Unroll
    void 'importFrom evaluates an entity with the evaluator nullable default (nullable = #defaultNullable)'() {
        given:
        var mappingContext = new KeyValueMappingContext('test')
        mappingContext.addPersistentEntity(ImportedEntity)
        var evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()), mappingContext, Collections.emptyMap(), true, defaultNullable)

        when:
        var constraints = evaluator.evaluate(ImportingEntityConstraints, false)

        then:
        constraints.name.nullable == defaultNullable
        !constraints.name.blank

        where:
        defaultNullable << [false, true]
    }

    class Constraints {
        String name
        static constraints = {
            name blank: false
        }
    }

    class MoreConstraints {
        String name
        static constraints = {
            importFrom Constraints
        }
    }
}

@Entity
class ImportedEntity {

    String name

    static constraints = {
        name(blank: false)
    }
}

class ImportingEntityConstraints {

    String name

    static constraints = {
        importFrom(ImportedEntity)
    }
}
