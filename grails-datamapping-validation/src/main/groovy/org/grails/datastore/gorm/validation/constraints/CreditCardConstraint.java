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

import org.apache.commons.validator.routines.CreditCardValidator;

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import grails.gorm.validation.ConstrainedProperty;

/**
 * Validates a credit card number.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class CreditCardConstraint extends AbstractConstraint {

    private final boolean creditCard;

    public CreditCardConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        creditCard = (boolean) this.constraintParameter;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (!creditCard) {
            return;
        }

        CreditCardValidator validator = new CreditCardValidator();

        if (!validator.isValid(propertyValue.toString())) {
            Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue };
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_CREDIT_CARD_MESSAGE_CODE,
                    ConstrainedProperty.CREDIT_CARD_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX, args);
        }
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Boolean)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.CREDIT_CARD_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be a boolean value");
        }
        return constraintParameter;
    }


    public String getName() {
        return ConstrainedProperty.CREDIT_CARD_CONSTRAINT;
    }

    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && String.class.isAssignableFrom(type);
    }
}
