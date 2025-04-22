/*
 * Copyright 2013 the original author or authors.
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
package org.grails.databinding.errors;

import grails.databinding.errors.BindingError;

/**
 * @author Jeff Brown
 * @since 2.3
 */
public class SimpleBindingError implements BindingError {

    private final Object object;
    private final String propertyName;
    private final Object rejectedValue;
    private final Throwable cause;

    public SimpleBindingError(Object object, String propertyName,
            Object rejectedValue, Throwable cause) {
        this.object = object;
        this.propertyName = propertyName;
        this.rejectedValue = rejectedValue;
        this.cause = cause;
    }

    public Object getObject() {
        return object;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public Throwable getCause() {
        return cause;
    }
}
