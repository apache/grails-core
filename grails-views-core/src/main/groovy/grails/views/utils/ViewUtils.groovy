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

package grails.views.utils

import groovy.transform.CompileStatic

/**
 * Utility methods for the views project
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class ViewUtils {

    /**
     * Retrieves a boolean value from a Map for the given key
     *
     * @param key The key that references the boolean value
     * @param map The map to look in
     * @return A boolean value which will be false if the map is null, the map doesn't contain the key or the value is false
     */
    static boolean getBooleanFromMap(String key, Map<?, ?> map, boolean defaultValue = false) {
        if (map == null) return defaultValue
        if (map.containsKey(key)) {
            Object o = map.get(key)
            if (o == null) return defaultValue
            if (o instanceof Boolean) {
                return (Boolean) o
            }
            return Boolean.valueOf(o.toString())
        }
        return defaultValue
    }

    /**
     * Obtains a list of strings from the map for the given key
     *
     * @param key The key
     * @param map The map
     * @return A list of strings
     */
    static List<String> getStringListFromMap(String key, Map map, List<String> defaultValue = []) {
        if (map == null) return defaultValue

        if (map.containsKey(key)) {
            def o = map.get(key)
            if (o instanceof Iterable) {
                return ((Iterable) o).toList() as List<String>
            } else {
                return Arrays.asList(o.toString())
            }
        } else {
            return defaultValue
        }
    }
}
