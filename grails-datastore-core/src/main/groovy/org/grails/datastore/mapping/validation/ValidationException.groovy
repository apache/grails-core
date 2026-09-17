/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.mapping.validation

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.util.ClassUtils
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError

/**
 * Exception thrown when a validation error occurs
 *
 * @author Graeme Rocher
 */
@CompileStatic
class ValidationException extends DataIntegrityViolationException {

    private static final long serialVersionUID = 1

    public static final Class<? extends RuntimeException> VALIDATION_EXCEPTION_TYPE = resolveValidationExceptionType()

    private static Class<? extends RuntimeException> resolveValidationExceptionType() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader()
        if (ClassUtils.isPresent('grails.validation.ValidationException', cl)) {
            Class<? extends RuntimeException> validationExceptionType
            try {
                validationExceptionType = (Class<? extends RuntimeException>) ClassUtils.forName('grails.validation.ValidationException', cl)
            } catch (Throwable ignored) {
                validationExceptionType = ValidationException
            }

            return validationExceptionType
        }
        return ValidationException
    }

    private final String fullMessage
    private final Errors errors

    ValidationException(String msg, Errors errors) {
        super(msg)
        fullMessage = formatErrors(errors, msg)
        this.errors = errors
    }

    /**
     * @return The errors object
     * @since 6.1.3
     */
    Errors getErrors() {
        return errors
    }

    @Override
    String getMessage() {
        return fullMessage
    }

    static String formatErrors(Errors errors, String msg) {
        String ls = System.getProperty('line.separator')
        StringBuilder b = new StringBuilder()
        if (msg != null) {
            b.append(msg).append(' : ').append(ls)
        }

        for (ObjectError error in errors.getAllErrors()) {
            b.append(ls)
                .append(' - ')
                .append(error)
                .append(ls)
        }
        return b.toString()
    }

    static RuntimeException newInstance(String message, Errors errors) {
        return DefaultGroovyMethods.newInstance(VALIDATION_EXCEPTION_TYPE, new Object[]{ message, errors })
    }

}
