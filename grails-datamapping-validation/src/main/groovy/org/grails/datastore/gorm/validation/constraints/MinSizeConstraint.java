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
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * Validates minimum size or length of the property, for strings and arrays
 * this is the length and collections the size.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class MinSizeConstraint extends AbstractConstraint {

    private final int minSize;

    public MinSizeConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.minSize = ((Number) this.constraintParameter).intValue();
    }

    /**
     * @return Returns the minSize.
     */
    public int getMinSize() {
        return minSize;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Number)) {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.MIN_SIZE_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be a of type [java.lang.Number]");
        }
        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.MIN_SIZE_CONSTRAINT;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && (
                String.class.isAssignableFrom(type) ||
                        Collection.class.isAssignableFrom(type) ||
                        type.isArray());
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        int length;
        if (propertyValue.getClass().isArray()) {
            length = Array.getLength(propertyValue);
        }
        else if (propertyValue instanceof Collection<?>) {
            length = ((Collection<?>)propertyValue).size();
        }
        else { // String
            length = ((String)propertyValue).length();
        }

        if (length < minSize) {
            Object[] args = { constraintPropertyName, constraintOwningClass, propertyValue, minSize};
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_MIN_SIZE_MESSAGE_CODE,
                    ConstrainedProperty.MIN_SIZE_CONSTRAINT + ConstrainedProperty.NOTMET_SUFFIX, args);
        }
    }
}
