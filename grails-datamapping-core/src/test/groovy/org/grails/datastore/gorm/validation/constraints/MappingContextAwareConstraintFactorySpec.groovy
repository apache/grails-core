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
package org.grails.datastore.gorm.validation.constraints

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification

class MappingContextAwareConstraintFactorySpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext("test")
    MappingContextAwareConstraintFactory factory =
            new MappingContextAwareConstraintFactory(UniqueConstraint, new StaticMessageSource(), mappingContext)

    void "builds a constraint when the owning class is a registered persistent entity"() {
        given:
        mappingContext.addPersistentEntities(FactoryBook)
        mappingContext.initialize()

        when:
        def constraint = factory.build(FactoryBook, 'title', true)

        then:
        constraint instanceof UniqueConstraint
    }

    void "returns null when the owning class is not a registered persistent entity"() {
        expect:
        factory.build(String, 'title', true) == null
    }
}

@Entity
class FactoryBook {
    String title
}
