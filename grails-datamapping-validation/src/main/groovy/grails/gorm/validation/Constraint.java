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

package grails.gorm.validation;

import org.springframework.validation.Errors;

/**
 * Defines a validateable constraint.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface Constraint {

    /**
     * Returns whether the constraint supports being applied against the specified type;
     *
     * @param type The type to support
     * @return true if the constraint can be applied against the specified type
     */
    @SuppressWarnings("rawtypes")
    boolean  supports(Class type);

    /**
     * Return whether the constraint is valid for the owning class
     *
     * @return true if it is
     */
    boolean isValid();

    /**
     * Validate this constraint against a property value. If implementation is vetoing (isVetoing() method
     * returns true), then it could return 'true' to stop further validation.
     *
     * @param target
     * @param propertyValue The property value to validate
     * @param errors The errors instance to record errors against
     */
    void validate(Object target, Object propertyValue, Errors errors);


    /**
     * @return The parameter to the constraint
     */
    Object getParameter();

    /**
     * @return The name of the constraint
     */
    String getName();

    /**
     * @return The property name of the constraint
     */
    String getPropertyName();

}