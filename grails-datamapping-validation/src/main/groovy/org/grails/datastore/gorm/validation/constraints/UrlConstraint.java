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

import java.util.List;

import org.apache.commons.validator.routines.RegexValidator;
import org.apache.commons.validator.routines.UrlValidator;

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import grails.gorm.validation.ConstrainedProperty;

/**
 * Validates a url.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class UrlConstraint extends AbstractConstraint {

    private final UrlValidator validator;

    public UrlConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.validator = (UrlValidator) this.constraintParameter;
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
        RegexValidator domainValidator;

        if (constraintParameter instanceof Boolean || constraintParameter instanceof UrlValidator) {
            // See https://github.com/grails/grails-core/issues/10993
            // When using "importFrom" the constraintParameter is the UrlValidator even if it was defined as
            // url: true, so we want the same behavior.
            domainValidator = null;
        }
        else if (constraintParameter instanceof String) {
            domainValidator = new RegexValidator((String) constraintParameter);
        }
        else if (constraintParameter instanceof List<?>) {
            List<?> regexpList = (List<?>) constraintParameter;
            domainValidator = new RegexValidator(regexpList.toArray(new String[regexpList.size()]));
        }
        else {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.URL_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be a boolean, string, or list value");
        }

        UrlValidator validator = new UrlValidator(domainValidator,
                UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.ALLOW_2_SLASHES);


        return validator;
    }

    public String getName() {
        return ConstrainedProperty.URL_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (!validator.isValid(propertyValue.toString())) {
            Object[] args = new Object[]{constraintPropertyName, constraintOwningClass, propertyValue};
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_URL_MESSAGE_CODE,
                    ConstrainedProperty.URL_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX, args);
        }
    }
}

