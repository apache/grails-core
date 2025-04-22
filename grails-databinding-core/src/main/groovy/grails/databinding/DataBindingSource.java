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
package grails.databinding;

import java.util.Set;

/**
 * A DataBindingSource is a lot like a Map but is read-only and is
 * tailored to support data binding.
 *
 * @since 3.0
 */
public interface DataBindingSource {
    /**
     *
     * @return the names of properties represented
     */
    Set<String> getPropertyNames();

    /**
     *
     * @param propertyName the name of a property
     * @return the value associated with propertyName, or null if propertyName is not represented
     */
    Object getPropertyValue(String propertyName);

    /**
     * Convencience operator overloading
     * @param propertyName the name of a property
     * @return the value associated with propertyName, or null if propertyName is not represented
     */
    Object getAt(String propertyName);

    /**
     *
     * @param propertyName the name of a property
     * @return true if propertyName is represented in the is binding source, otherwise false
     */
    boolean containsProperty(String propertyName);

    /**
     *
     * @return true if this binding source contains an identifier for binding
     */
    boolean hasIdentifier();

    /**
     *
     * @return the identifier value for binding or null if no identifier is represented by this binding source
     */
    Object getIdentifierValue();

    /**
     *
     * @return the number of properties represented by this binding source
     */
    int size();

    /**
     *
     * @return true if GORM operations should be enabled when binding with this DataBindingSource
     */
    boolean isDataSourceAware();

    /**
     *
     * @param isDataSourceAware true if GORM operations should be enabled when binding with this DataBindingSource
     */
    void setDataSourceAware(boolean isDataSourceAware);
}
