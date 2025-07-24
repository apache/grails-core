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
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

import grails.gorm.validation.ConstrainedProperty;

/**
 * A Constraint that validates a string is not blank.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class BlankConstraint extends AbstractVetoingConstraint {

    private final boolean blank;

    public BlankConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.blank = (Boolean) this.constraintParameter;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && String.class.isAssignableFrom(type);
    }

    @Override
    public Object getParameter() {
        return blank;
    }

    public boolean isBlank() {
        return blank;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Boolean)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.BLANK_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be a boolean value");
        }
        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.BLANK_CONSTRAINT;
    }

    @Override
    protected boolean skipBlankValues() {
        return false;
    }

    @Override
    protected boolean processValidateWithVetoing(Object target, Object propertyValue, Errors errors) {
        if (!blank && propertyValue instanceof String && !StringUtils.hasText((CharSequence) propertyValue)) {
            Object[] args = new Object[] { constraintPropertyName, constraintOwningClass };
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_BLANK_MESSAGE_CODE,
                    ConstrainedProperty.BLANK_CONSTRAINT, args);
            // empty string is caught by 'blank' constraint, no addition validation needed
            return true;
        }
        return false;
    }
}
