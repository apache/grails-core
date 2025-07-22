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

package org.grails.beans.support;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Fixed version of Spring's PropertiesEditor that converts all keys and values to String values
 * 
 * java.util.Properties should only contain String keys and values
 * 
 * @author Lari Hotari
 * @since 2.3.6
 *
 */
public class PropertiesEditor extends org.springframework.beans.propertyeditors.PropertiesEditor {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setValue(Object value) {
        if (!(value instanceof Properties) && value instanceof Map) {
            Properties props = new Properties();
            for(Map.Entry entry : (Set<Map.Entry>)((Map)value).entrySet()) {
                props.put(String.valueOf(entry.getKey()), entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
            }
            super.setValue(props);
        }
        else {
            super.setValue(value);
        }
    }
}
