/* Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.marshaller;

import org.grails.web.converters.Converter;

/**
 * A marshaller capable of including or excluding properties
 *
 * @author Graeme Rocher
 * @since 2.3
 */
public abstract class IncludeExcludePropertyMarshaller<T extends Converter> implements ObjectMarshaller<T> {

    protected boolean shouldInclude(Object object, String propertyName) {
        return includesProperty(object, propertyName) && !excludesProperty(object, propertyName);
    }

    /**
     * Override for custom exclude logic
     *
     * @param object The object
     * @param property The property
     * @return True if it is excluded
     */
    protected boolean excludesProperty(Object object, String property) {
        return false;
    }

    /**
     * Override for custom include logic
     *
     * @param object The object
     * @param property The property
     * @return True if it is included
     */
    protected boolean includesProperty(Object object, String property) {
        return true;
    }
}
