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
import groovy.lang.Range;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * Validates a range.
 *
 * @author Graeme Rocher
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class RangeConstraint extends AbstractConstraint {

    private final Range range;

    public RangeConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.range = (Range) this.constraintParameter;
    }

    /**
     * @return Returns the range.
     */
    public Range getRange() {
        return range;
    }

    /* (non-Javadoc)
     * @see org.apache.groovy.grails.validation.Constraint#supports(java.lang.Class)
     */
    public boolean supports(Class type) {
        return type != null && (Comparable.class.isAssignableFrom(type) ||
                ClassUtils.isAssignableOrConvertibleFrom(Number.class, type));
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Range)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.RANGE_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be a of type [groovy.lang.Range]");
        }
        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.RANGE_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (range.contains(propertyValue)) {
            return;
        }

        Object[] args = new Object[]{constraintPropertyName, constraintOwningClass,
                propertyValue, range.getFrom(), range.getTo()};

        Comparable from = range.getFrom();
        Comparable to = range.getTo();

        boolean isNumberRange = from instanceof Number;
        if (isNumberRange) {
            // Upgrade the numbers to Long, so all integer types can be compared.
            from = ((Number) from).longValue();
            to = ((Number) to).longValue();
            if (propertyValue instanceof Number) {
                propertyValue = ((Number) propertyValue).longValue();
            } else if (propertyValue instanceof CharSequence) {
                try {
                    propertyValue = Long.valueOf(propertyValue.toString());
                } catch (NumberFormatException e) {
                    rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_RANGE_MESSAGE_CODE,
                            ConstrainedProperty.RANGE_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX, args);
                }
            }
        }

        if (from.compareTo(propertyValue) > 0) {
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_RANGE_MESSAGE_CODE,
                    ConstrainedProperty.RANGE_CONSTRAINT + ConstrainedProperty.TOOSMALL_SUFFIX, args);
        } else if (to.compareTo(propertyValue) < 0) {
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_RANGE_MESSAGE_CODE,
                    ConstrainedProperty.RANGE_CONSTRAINT + ConstrainedProperty.TOOBIG_SUFFIX, args);
        }
    }
}
