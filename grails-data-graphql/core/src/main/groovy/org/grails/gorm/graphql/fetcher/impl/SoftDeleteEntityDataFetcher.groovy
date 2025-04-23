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

package org.grails.gorm.graphql.fetcher.impl

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Used to soft delete entity instances. Alternative to
 * {@link DeleteEntityDataFetcher} to allow users to register
 * their own instances.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class SoftDeleteEntityDataFetcher<T> extends DeleteEntityDataFetcher<T> {

    final String propertyName
    final Object value
    final MappingContext mappingContext

    SoftDeleteEntityDataFetcher(PersistentEntity entity, String propertyName, Object value) {
        super(entity)
        this.mappingContext = entity.mappingContext
        this.propertyName = propertyName
        this.value = value
    }

    @Override
    protected void deleteInstance(GormEntity instance) {
        EntityAccess entityAccess = mappingContext.createEntityAccess(entity, instance)
        entityAccess.setProperty(propertyName, value)
        instance.markDirty(propertyName)
        instance.save()
    }
}
