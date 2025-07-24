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

package org.grails.web.plugins.support

import groovy.transform.CompileStatic

import org.springframework.validation.Validator

import grails.gorm.validation.ConstrainedEntity
import grails.gorm.validation.ConstrainedProperty
import grails.validation.Constrained
import grails.validation.ConstrainedDelegate
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.validation.discovery.ConstrainedDiscovery

/**
 * Discovers the default constrained properties for a domain class
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
class DefaultConstrainedDiscovery implements ConstrainedDiscovery {
    @Override
    Map<String, Constrained> findConstrainedProperties(PersistentEntity entity) {
        Validator validator = entity.getMappingContext().getEntityValidator(entity)
        if(validator instanceof ConstrainedEntity) {
            ConstrainedEntity constrainedEntity = (ConstrainedEntity)validator
            Map<String, ConstrainedProperty> constrainedProperties = constrainedEntity.getConstrainedProperties()
            return adaptConstraints(constrainedProperties)
        }
        return Collections.emptyMap()
    }

    private static Map<String, Constrained> adaptConstraints(Map<String, ConstrainedProperty> evaluated) {
        Map<String, Constrained> finalConstraints = new LinkedHashMap<>(evaluated.size());
        for (Map.Entry<String, ConstrainedProperty> entry : evaluated.entrySet()) {
            finalConstraints.put(entry.getKey(), new ConstrainedDelegate(entry.getValue()));
        }
        return finalConstraints;
    }

}
