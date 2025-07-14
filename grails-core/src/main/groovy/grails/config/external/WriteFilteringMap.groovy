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
package grails.config.external

import groovy.transform.CompileStatic

@CompileStatic
class WriteFilteringMap implements Map<String, Object> {

    String keyPrefix
    private Map<String, Object> proxied // source map
    @Delegate
    private Map<String, Object> overlap  // written values, flattened -- shared
    private Map<String, Object> nestedDestinationMap // written keys at this level

    WriteFilteringMap(Map source = [:]) {
        this(source, '', [:])
    }

    private WriteFilteringMap(Map<String, Object> nestedSource, String nestedKey, Map destination) {
        this.proxied = nestedSource
        this.keyPrefix = nestedKey
        this.nestedDestinationMap = destination
        this.overlap = initialize(proxied, keyPrefix, nestedDestinationMap)
    }

    Map<String, Object> getWrittenValues() {
        return nestedDestinationMap.asImmutable()
    }

    private static Map<String, Object> initialize(Map<String, Object> proxied, String keyPrefix, Map<String, Object> nestedDestinationMap) {
        Map overlap = [:] as Map<String, Object>
        proxied.each { String k, Object original ->
            Object toInsert
            if (original == null || original in Map) {
                toInsert = new WriteFilteringMap(
                        (original ?: Collections.emptyMap()) as Map,
                        "$keyPrefix$k." as String, nestedDestinationMap)
            } else {
                toInsert = original
            }
            //nestedDestinationMap.put(keyPrefix + k, toInsert)
            overlap.put(k, toInsert)
        }
        return overlap
    }

    /**
     * The map is infinite, either returning a value or an empty next level map
     * @param key
     * @return current value or an empty map
     */
    @Override
    Object get(Object key) {
        return overlap.get(key) ?:
                new WriteFilteringMap(
                        Collections.emptyMap(),
                        "$keyPrefix$key." as String, nestedDestinationMap
                )
    }

    @Override
    Object put(String key, Object value) {
        nestedDestinationMap.put(keyPrefix + key, value)
        return overlap.put(key, value)
    }

    @Override
    Object remove(Object key) {
        nestedDestinationMap.remove(keyPrefix + key)
        return overlap.remove(key)
    }

    @Override
    void putAll(Map<? extends String, ?> m) {
        m.each { k, v ->
            this.put(k, v)
        }
    }
}

