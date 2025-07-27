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
package org.grails.web.databinding.converters

import java.lang.reflect.ParameterizedType

import groovy.transform.CompileStatic

import grails.databinding.DataBindingSource
import grails.databinding.TypedStructuredBindingEditor

/**
 * An abstract base class for StructuredBindingEditor instances which can be auto-discovered
 * as beans in the Spring application context
 *
 * @see StructuredBindingEditor
 *
 * @since 2.3.4
 */
@CompileStatic
abstract class AbstractStructuredBindingEditor<T> implements TypedStructuredBindingEditor<T> {

    final Class<T> targetType

    AbstractStructuredBindingEditor() {
        def superClass = getClass().genericSuperclass
        def type = (ParameterizedType) superClass
        def types = type.actualTypeArguments
        targetType = types[0]
    }

    @Override
    T getPropertyValue(Object obj, String propertyName, DataBindingSource bindingSource) {
        def propertyMap = getPropertyValuesMap(propertyName, bindingSource)
        getPropertyValue propertyMap
    }

    abstract T getPropertyValue(Map values)

    /**
     * A convenience method for extracting structured values from a DataBindingSource.
     * The method will look for all properties in bindingSource which have a key which
     * begins with propertyPrefix followed by an underscore and put each of those values
     * in the resulting Map with a key that matches the original key with the propertyName
     * plus prefix removed.  For example, if propertyPrefix is &quot;address&quot; and
     * bindingSource contains the key &quot;address_city&quot; with a value of &quot;St. Louis&quot;
     * then the resulting Map will contain an entry such that the key is &quot;city&quot;
     * with a value of &quot;St. Louis&quot;
     *
     * @param propertyPrefix The property name to extract structured values for
     * @param bindingSource the DataBindingSource to extract structured values from
     * @return A Map containing keys and values as described above.
     */
    Map<String, Object> getPropertyValuesMap(String propertyPrefix, DataBindingSource bindingSource) {
        Map<String, Object> valuesMap = [:]
        def prefix = propertyPrefix + '_'
        for(String key : bindingSource.propertyNames) {
            if(key.startsWith(prefix) && key.size() > prefix.size()) {
                def propName = key[prefix.size()..-1]
                valuesMap[propName] = bindingSource.getPropertyValue(key)
            }
        }
        valuesMap as Map<String, Object>
    }
}
