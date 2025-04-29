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

import grails.gorm.validation.Constraint
import org.grails.datastore.gorm.validation.constraints.factory.DefaultConstraintFactory
import org.grails.datastore.mapping.model.MappingContext
import org.springframework.context.MessageSource

/**
 * A constraint that restricts constraints to be applicable only to a given {@link org.grails.datastore.mapping.model.MappingContext}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class MappingContextAwareConstraintFactory extends DefaultConstraintFactory {
    final MappingContext mappingContext

    MappingContextAwareConstraintFactory(Class<? extends Constraint> constraintClass, MessageSource messageSource, MappingContext mappingContext, List<Class> targetTypes = [Object]) {
        super(constraintClass, messageSource, targetTypes)
        this.mappingContext = mappingContext
    }

    @Override
    Constraint build(Class owner, String property, Object constrainingValue) {
        if(mappingContext.getPersistentEntity(owner.name) != null) {
            return super.build(owner, property, constrainingValue)
        }
        return null
    }
}
