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

import java.lang.annotation.ElementType

import groovy.transform.CompileStatic

import jakarta.validation.Path
import jakarta.validation.TraversableResolver

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * A {@link TraversableResolver} that uses the {@link MappingContext} to establish whether validation can cascade.
 * While the GORM validator cascades to the persistent associations of an entity
 * ({@link GormValidatorAdapter#CASCADE_VALIDATION} is {@code false}), Bean Validation cascades only through the
 * properties marked {@code @Valid} that are not persistent associations, such as a transient property.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class MappingContextTraversableResolver implements TraversableResolver {

    final MappingContext mappingContext
    final ProxyHandler proxyHandler

    MappingContextTraversableResolver(MappingContext mappingContext) {
        this.mappingContext = mappingContext
        this.proxyHandler = mappingContext.proxyHandler
    }

    @Override
    boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        if (!proxyHandler.isInitialized(traversableObject)) {
            return false
        }
        String propertyName = traversableProperty.name
        if (propertyName == null) {
            // nothing to read for a node without a property name; Hibernate Validator does not consult the resolver
            // for a class level constraint, so this is only a guard
            return true
        }
        Object value
        try {
            value = ClassPropertyFetcher.getInstancePropertyValue(traversableObject, propertyName)
        }
        catch (RuntimeException ignored) {
            return false
        }
        // a null value has nothing to initialize, and has to be reachable for constraints such as @NotNull to see it
        return value == null || proxyHandler.isInitialized(value)
    }

    @Override
    boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        Class type = proxyHandler.getProxiedClass(traversableObject)
        PersistentEntity entity = mappingContext.getPersistentEntity(type.name)
        if (GormValidatorAdapter.CASCADE_VALIDATION.get() == Boolean.FALSE) {
            // the GORM validator cascades to the persistent associations, Bean Validation to every other @Valid property
            return !(entity?.getPropertyByName(traversableProperty.name) instanceof Association)
        }
        if (entity != null) {
            PersistentEntity currentEntity = entity
            for (Path.Node n in pathToTraversableObject) {
                if (currentEntity == null) break
                PersistentProperty prop = currentEntity.getPropertyByName(n.name)
                if (prop instanceof Association) {
                    Association association = (Association) prop
                    if (association.isOwningSide()) {
                        currentEntity = association.associatedEntity
                    }
                    else {
                        currentEntity = null
                    }
                }
                else {
                    currentEntity = null
                }
            }
            if (currentEntity != null) {
                return true
            }
        }
        return false
    }
}
