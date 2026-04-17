/*
 *
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.grails.data.simple.core

import org.apache.grails.data.testing.tck.base.GrailsDataTckManager
import org.grails.datastore.gorm.Birthday
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query.Between
import org.grails.datastore.mapping.query.Query.PropertyCriterion
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.query.SimpleMapQuery
import org.grails.datastore.mapping.simple.query.SimpleMapResultList
import org.springframework.context.support.GenericApplicationContext
import org.springframework.util.StringUtils
import org.springframework.validation.Errors
import org.springframework.validation.Validator

/**
 * @author graemerocher
 */
class GrailsDataCoreTckManager extends GrailsDataTckManager {

    @Override
    Session createSession() {
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def simple = new SimpleMapDatastore(ctx)

        simple.mappingContext.mappingFactory.registerCustomType(new AbstractMappingAwareCustomTypeMarshaller<Birthday, Map, SimpleMapResultList>(Birthday) {
            @Override
            protected Object writeInternal(PersistentProperty property, String key, Birthday value, Map nativeTarget) {
                if (value == null) {
                    return null
                }

                final converted = value.date.time
                nativeTarget.put(key, converted)
                return converted
            }

            @Override
            protected void queryInternal(PersistentProperty property, String key, PropertyCriterion criterion, SimpleMapResultList nativeQuery) {
                SimpleMapQuery query = nativeQuery.query
                def handler = query.handlers[criterion.getClass()]

                if (criterion instanceof Between) {
                    criterion.from = criterion.from.date.time
                    criterion.to = criterion.to.date.time
                    nativeQuery.results << handler?.call(criterion, property) ?: []
                }
                else {
                    criterion.value = criterion.value.date.time
                    nativeQuery.results << handler?.call(criterion, property) ?: []
                }
            }

            @Override
            protected Birthday readInternal(PersistentProperty property, String key, Map nativeSource) {
                final num = nativeSource.get(key)
                if (num instanceof Long) {
                    return new Birthday(new Date(num))
                }
                return null
            }
        })
        for (cls in domainClasses) {
            simple.mappingContext.addPersistentEntity(cls)
        }

        PersistentEntity entity = simple.mappingContext.persistentEntities.find {
            PersistentEntity e -> e.name.contains("TestEntity")}

        simple.mappingContext.addEntityValidator(entity, [
                supports: { Class c -> true },
                validate: { Object o, Errors errors ->
                    if (!StringUtils.hasText(o.name)) {
                        errors.rejectValue("name", "name.is.blank")
                    }
                }
        ] as Validator)


        simple.connect()
    }
}
