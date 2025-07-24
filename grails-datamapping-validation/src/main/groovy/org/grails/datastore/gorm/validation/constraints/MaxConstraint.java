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

package org.grails.datastore.gorm.validation.constraints;

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import grails.gorm.validation.ConstrainedProperty;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.ClassUtils;

/**
 * Implements a maximum value constraint.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class MaxConstraint extends AbstractConstraint {

    private final Comparable maxValue;

    public MaxConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.maxValue = (Comparable) this.constraintParameter;
    }

    /**
     * @return Returns the maxValue.
     */
    public Comparable getMaxValue() {
        return maxValue;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    public boolean supports(Class type) {
        return type != null && (
                Comparable.class.isAssignableFrom(type) ||
                        ClassUtils.isAssignableOrConvertibleFrom(Number.class, type));
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (constraintParameter == null) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.MAX_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass + "] cannot be null");
        }

        if (!(constraintParameter instanceof Comparable<?>) && (!constraintParameter.getClass().isPrimitive())) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.MAX_CONSTRAINT + "] of property [" + constraintPropertyName +
                    "] of class ["+constraintOwningClass + "] must implement the interface [java.lang.Comparable]");
        }

        Class<?> propertyClass = ClassPropertyFetcher.getPropertyType(constraintOwningClass, constraintPropertyName);
        if (!ClassUtils.isAssignableOrConvertibleFrom(constraintParameter.getClass(), propertyClass)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.MAX_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be the same type as property: [" + propertyClass.getName() + "]");
        }
        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.MAX_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (maxValue.compareTo(propertyValue) < 0) {
            Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue, maxValue  };
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_MAX_MESSAGE_CODE,
                    ConstrainedProperty.MAX_CONSTRAINT + ConstrainedProperty.EXCEEDED_SUFFIX, args);
        }
    }
}
