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

package org.grails.datastore.gorm.validation.jakarta

import grails.gorm.services.Service
import groovy.transform.CompileStatic
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.springframework.validation.Errors
import org.springframework.validation.MapBindingResult

/**
 * Utility methods for handling ConstraintViolationException
 *
 * @author Graeme Rocher
 * @since 6.1.3
 */
@CompileStatic
class ConstraintViolationUtils {

    /**
     * Converts a ConstraintViolationException to errors
     *
     * @param object The validated object
     * @param e The exception
     * @return The errors
     */
    static Errors asErrors(Object object, ConstraintViolationException e) {
        Set<ConstraintViolation<?>> constraintViolations = e.constraintViolations
        return asErrors(object, constraintViolations)
    }

    /**
     * Converts a ConstraintViolation instances to errors
     *
     * @param object The validated object
     * @param e The exception
     * @return The errors
     */
    static Errors asErrors(Object object, Set<ConstraintViolation> constraintViolations) {
        Service ann = object.getClass().getAnnotation(Service)
        String objectName = ann != null ? ann.name() : object.getClass().simpleName
        Map errorMap = [:]
        Errors errors = new MapBindingResult(errorMap, objectName)
        for (violation in constraintViolations) {
            String property = violation.propertyPath.last().name
            errorMap.put(property, violation.invalidValue)
            String code = "${objectName}.${violation.propertyPath}"
            String message = "${property} $violation.message"
            errors.rejectValue(property, code, [violation.invalidValue] as Object[], message)
        }
        return errors
    }
}
