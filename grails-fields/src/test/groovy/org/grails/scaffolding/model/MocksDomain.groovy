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
package org.grails.scaffolding.model

import grails.gorm.validation.PersistentEntityValidator
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator

@CompileStatic
trait MocksDomain {

    PersistentEntity mockDomainClass(MappingContext mappingContext, Class clazz) {
        PersistentEntity persistentEntity = mappingContext.addPersistentEntity(clazz)
        mappingContext.addEntityValidator(persistentEntity,
                new PersistentEntityValidator(
                        persistentEntity,
                        new StaticMessageSource(),
                        new DefaultConstraintEvaluator()
                )
        )
        persistentEntity
    }

    PersistentEntity mockDomainClassEntityValidator(MappingContext mappingContext, Class clazz) {
        PersistentEntity persistentEntity = mappingContext.addPersistentEntity(clazz)
        def registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        Validator validator = registry.getValidator(persistentEntity)
        mappingContext.addEntityValidator(persistentEntity, validator)
        persistentEntity
    }

    DomainPropertyFactory mockDomainPropertyFactory(MappingContext mappingContext) {
        DomainPropertyFactory domainPropertyFactory = new DomainPropertyFactoryImpl()
        domainPropertyFactory.trimStrings = true
        domainPropertyFactory.convertEmptyStringsToNull = true
        domainPropertyFactory.grailsDomainClassMappingContext = mappingContext
        domainPropertyFactory
    }
}