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

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import grails.gorm.validation.ConstrainedProperty;

/**
 * A constraint that validates the property is contained within the supplied list.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class InListConstraint extends AbstractConstraint {

    protected final List<?> list;

    public InListConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.list = (List<?>) this.constraintParameter;
    }

    /**
     * @return Returns the list.
     */
    public List<?> getList() {
        return list;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null;
    }

    public String getName() {
        return ConstrainedProperty.IN_LIST_CONSTRAINT;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof List<?>)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.IN_LIST_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must implement the interface [java.util.List]");
        }
        return constraintParameter;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        // Check that the list contains the given value. If not, add an error.
        if (!list.contains(propertyValue)) {
            Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue, list };
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_NOT_INLIST_MESSAGE_CODE,
                    ConstrainedProperty.NOT_PREFIX + ConstrainedProperty.IN_LIST_CONSTRAINT, args);
        }
    }
}
