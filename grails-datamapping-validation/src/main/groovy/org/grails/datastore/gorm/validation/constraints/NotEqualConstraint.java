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

import grails.gorm.validation.ConstrainedProperty;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * Validates not equal to something.
 */
public class NotEqualConstraint extends AbstractConstraint {

    public NotEqualConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null;
    }

    public String getName() {
        return ConstrainedProperty.NOT_EQUAL_CONSTRAINT;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (constraintParameter == null) {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.NOT_EQUAL_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] cannot be null");
        }

        Class<?> propertyClass = ClassPropertyFetcher.getPropertyType(constraintOwningClass, constraintPropertyName);
        if (!ClassUtils.isAssignableOrConvertibleFrom(constraintParameter.getClass(), propertyClass) && propertyClass != null) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.NOT_EQUAL_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be the same type as property: [" + propertyClass.getName() + "]");
        }
        return constraintParameter;
    }

    /**
     * @return Returns the notEqualTo.
     */
    public Object getNotEqualTo() {
        return constraintParameter;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (constraintParameter.equals(propertyValue)) {
            Object[] args = new Object[]{constraintPropertyName, constraintOwningClass, propertyValue, constraintParameter};
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_NOT_EQUAL_MESSAGE_CODE,
                    ConstrainedProperty.NOT_EQUAL_CONSTRAINT, args);
        }
    }
}
