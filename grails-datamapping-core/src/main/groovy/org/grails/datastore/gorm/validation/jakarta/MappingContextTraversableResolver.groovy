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

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.proxy.ProxyHandler

import jakarta.validation.Path
import jakarta.validation.TraversableResolver
import java.lang.annotation.ElementType

/**
 * A {@link TraversableResolver} that uses the {@link MappingContext} to establish whether validation can cascade
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
        return proxyHandler.isInitialized(traversableObject) && proxyHandler.isInitialized(traversableObject, traversableProperty.name)
    }

    @Override
    boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        Class type = proxyHandler.getProxiedClass(traversableObject)
        PersistentEntity entity = mappingContext.getPersistentEntity(type.name)
        if(entity != null) {
            PersistentEntity currentEntity = entity
            for(Path.Node n in pathToTraversableObject) {
                if(currentEntity == null) break
                PersistentProperty prop = currentEntity.getPropertyByName(n.name)
                if(prop instanceof Association) {
                    Association association = (Association)prop
                    if(association.isOwningSide()) {
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
            if(currentEntity != null) {
                return true
            }
        }
        return false
    }
}
