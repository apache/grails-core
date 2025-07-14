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

package org.grails.datastore.gorm.validation.constraints.factory

import grails.gorm.validation.Constraint
import grails.gorm.validation.exceptions.ValidationConfigurationException
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.NullableConstraint
import org.grails.datastore.mapping.reflect.ClassUtils
import org.springframework.context.MessageSource

import java.beans.Introspector
import java.lang.reflect.Constructor

/**
 * A default factory for creating constraints
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class DefaultConstraintFactory implements ConstraintFactory {

    final Class<? extends Constraint> type
    final String name
    final MessageSource messageSource
    final List<Class> targetTypes

    protected final Constructor constraintConstructor

    DefaultConstraintFactory(Class<? extends Constraint> constraintClass, MessageSource messageSource, List<Class> targetTypes = [Object]) {
        this.type = constraintClass
        this.name = Introspector.decapitalize(constraintClass.simpleName) - "Constraint"
        this.messageSource = messageSource
        this.targetTypes = targetTypes

        try {
            constraintConstructor = constraintClass.getConstructor(Class, String, Object, MessageSource)
        } catch (Throwable e) {
            throw new ValidationConfigurationException("Invalid constraint type [$constraintClass] must have a 4 argument constructor accepting the Class, propertyName, constrainedObject and MesssageSource. Message: $e.message", e)
        }
    }

    @Override
    boolean supports(Class targetType) {
        if (NullableConstraint.isAssignableFrom(type)) {
            return !targetType.isPrimitive()
        } else {
            return this.targetTypes.any() { Class type -> ClassUtils.isAssignableOrConvertibleFrom(type, targetType) }
        }
    }

    @Override
    Constraint build(Class owner, String property, Object constrainingValue) {
        return type.newInstance(owner, property, constrainingValue, messageSource)
    }
}
