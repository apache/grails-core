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

package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * A class to easily render a select element based on a given set
 * of options and a default option
 *
 * @author James Kleeh
 * @param <T> Any type
 */
trait MapToSelectInputRenderer<T> implements DomainInputRenderer {

    /**
     * Defines how a given <T> should be displayed in a select element
     *
     * @param t An instance of T
     * @return The inner text of an option element
     */
    abstract String getOptionValue(T t)

    /**
     * Defines how a given <T> should be uniquely identified in a select element
     *
     * @param t An instance of T
     * @return The value attribute of an option element
     */
    abstract String getOptionKey(T t)

    /**
     * @return The default <T> to be selected in the select element
     */
    abstract T getDefaultOption()

    /**
     * Builds the options to be displayed
     *
     * @return The map of options where the key will be to the option value and value will be the option text
     */
    abstract Map<String, String> getOptions()

    /** @see DomainInputRenderer#renderInput() **/
    Closure renderInput(Map defaultAttributes, DomainProperty property) {
        String selected = getOptionKey(defaultOption)

        return { ->
            select(defaultAttributes) {
                options.each { String key, String value ->
                    Map attrs = [value: key]
                    if (selected == key) {
                        attrs.selected = ""
                    }
                    option(value, attrs)
                }
            }
        }
    }

}
