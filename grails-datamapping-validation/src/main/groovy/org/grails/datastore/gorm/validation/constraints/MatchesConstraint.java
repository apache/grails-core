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

/**
 * Validates the property against a supplied regular expression.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class MatchesConstraint extends AbstractConstraint {

    private final String regex;

    public MatchesConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.regex = this.constraintParameter.toString();
    }

    /**
     * @return Returns the regex.
     */
    public String getRegex() {
        return regex;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && String.class.isAssignableFrom(type);
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof CharSequence)) {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.MATCHES_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be of type [CharSequence]");
        }
        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.MATCHES_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (propertyValue.toString().matches(regex)) {
            return;
        }

        rejectValue(target, errors, ConstrainedProperty.DEFAULT_DOESNT_MATCH_MESSAGE_CODE,
                ConstrainedProperty.MATCHES_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX,
                new Object[] { constraintPropertyName, constraintOwningClass, propertyValue, regex });
    }
}

