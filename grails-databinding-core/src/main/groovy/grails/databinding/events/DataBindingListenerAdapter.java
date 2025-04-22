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
 * @author Jeff Brown
 * @since 3.0
 * @see DataBindingListener
 */
public class DataBindingListenerAdapter implements DataBindingListener {

    public boolean supports(Class<?> clazz) {
        return true;
    }

    public Boolean beforeBinding(Object target, Object errors) {
        return true;
    }

    public Boolean beforeBinding(Object obj, String propertyName, Object value, Object errors) {
        return true;
    }

    public void afterBinding(Object obj, String propertyName, Object errors) {
    }

    public void afterBinding(Object target, Object errors) {
    }

    public void bindingError(BindingError error, Object errors) {
    }
}
