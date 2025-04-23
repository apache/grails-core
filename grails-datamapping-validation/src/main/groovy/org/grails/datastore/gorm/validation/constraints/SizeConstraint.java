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
import groovy.lang.IntRange;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * Validates size of the property, for strings and arrays
 * this is the length, collections the size and numbers the value.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class SizeConstraint extends AbstractConstraint {

    private IntRange range;

    public SizeConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.range = (IntRange) this.constraintParameter;
    }

    /**
     * @return Returns the range.
     */
    public IntRange getRange() {
        return range;
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
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof IntRange)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.SIZE_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be a of type [groovy.lang.IntRange]");
        }
        return constraintParameter;
    }


    public String getName() {
        return ConstrainedProperty.SIZE_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        Object[] args = { constraintPropertyName, constraintOwningClass, propertyValue,
                range.getFrom(), range.getTo() };

        int size;
        if (propertyValue.getClass().isArray()) {
            size = Array.getLength(propertyValue);
        }
        else if (propertyValue instanceof Collection<?>) {
            size = ((Collection<?>)propertyValue).size();
        }
        else { // String
            size = ((String)propertyValue).length();
        }

        if (!range.contains(size)) {
            if (range.getFrom().compareTo(size) == 1) {
                rejectValue(args, errors, target, ConstrainedProperty.TOOSMALL_SUFFIX);
            }
            else if (range.getTo().compareTo(size) == -1) {
                rejectValue(args, errors, target, ConstrainedProperty.TOOBIG_SUFFIX);
            }
        }
    }

    private void rejectValue(Object[] args, Errors errors, Object target, String suffix) {
        rejectValue(target,errors, ConstrainedProperty.DEFAULT_INVALID_SIZE_MESSAGE_CODE,
                ConstrainedProperty.SIZE_CONSTRAINT + suffix, args);
    }
}
