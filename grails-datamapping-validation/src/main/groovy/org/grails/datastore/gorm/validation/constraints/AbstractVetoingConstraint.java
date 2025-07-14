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

import grails.gorm.validation.VetoingConstraint;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * A constraint that can veto further constraint processing
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractVetoingConstraint extends AbstractConstraint implements VetoingConstraint {

    public AbstractVetoingConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
    }

    public boolean validateWithVetoing(Object target, Object propertyValue, Errors errors) {
        checkState();
        if (propertyValue == null && skipNullValues()) {
            return false;
        }

        return processValidateWithVetoing(target, propertyValue, errors);
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        processValidateWithVetoing(target, propertyValue, errors);
    }

    protected abstract boolean processValidateWithVetoing(Object target, Object propertyValue, Errors errors);
}
