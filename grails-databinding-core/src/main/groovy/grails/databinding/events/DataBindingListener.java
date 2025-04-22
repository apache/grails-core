/*
 * Copyright 2014 the original author or authors.
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
package grails.databinding.events;

import grails.databinding.errors.BindingError;

/**
 * A listener which will be notified of events generated during data binding.
 *
 * @author Jeff Brown
 * @since 3.0
 * @see DataBindingListenerAdapter
 */
public interface DataBindingListener {

    /**
     * @return true if the listener is interested in events for the specified type.
     */
    boolean supports(Class<?> clazz);

    /**
     * Called when data binding is about to start.
     * 
     * @param target The object data binding is being imposed upon
     * @param errors the Spring Errors instance (a org.springframework.validation.BindingResult)
     * @return true if data binding should continue
     */
    Boolean beforeBinding(Object target, Object errors);

    /**
     * Called when data binding is about to imposed on a property
     *
     * @param target The object data binding is being imposed upon
     * @param propertyName The name of the property being bound to
     * @param value The value of the property being bound
     * @param errors the Spring Errors instance (a org.springframework.validation.BindingResult)
     * @return true if data binding should continue, otherwise return false
     */
    Boolean beforeBinding(Object target, String propertyName, Object value, Object errors);

    /**
     * Called after data binding has been imposed on a property
     *
     * @param target The object data binding is being imposed upon
     * @param propertyName The name of the property that was bound to
     * @param errors the Spring Errors instance (a org.springframework.validation.BindingResult)
     */
    void afterBinding(Object target, String propertyName, Object errors);

    /**
     * Called after data binding has finished.
     *  
     * @param target The object data binding is being imposed upon
     * @param errors the Spring Errors instance (a org.springframework.validation.BindingResult)
     */
    void afterBinding(Object target, Object errors);

    /**
     * Called when an error occurs binding to a property
     * @param error encapsulates information about the binding error
     * @param errors the Spring Errors instance (a org.springframework.validation.BindingResult)
     * @see BindingError
     */
    void bindingError(BindingError error, Object errors);
}
