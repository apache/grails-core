/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.validation;

import org.springframework.validation.BeanPropertyBindingResult;

/**
 * Models validation errors
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class ValidationErrors extends BeanPropertyBindingResult {
    private static final long serialVersionUID = 1;

    /**
     * Creates a new instance of the {@link org.springframework.validation.BeanPropertyBindingResult} class.
     *
     * @param target     the target bean to bind onto
     * @param objectName the name of the target object
     */
    public ValidationErrors(Object target, String objectName) {
        super(target, objectName);
    }
    /**
     * Creates a new instance of the {@link org.springframework.validation.BeanPropertyBindingResult} class.
     *
     * @param target     the target bean to bind onto
     */
    public ValidationErrors(Object target) {
        super(target, target.getClass().getName());
    }

    public Object getAt(String field) {
        return getFieldError(field);
    }

    public void putAt(String field, String errorCode) {
        rejectValue(field, errorCode);
    }
}
