/*
 * Copyright 2015 original authors
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
package grails.config

import groovy.transform.CompileStatic


/**
 * Cached access to Config.toProperties to avoid repeated calls
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class ConfigProperties extends Properties {

    private final Config config

    ConfigProperties(Config config) {
        this.config = config
    }

    @Override
    Set<String> stringPropertyNames() {
        return config.keySet()
    }

    @Override
    Enumeration<?> propertyNames() {
        def i = config.keySet().iterator()
        return ([
            hasMoreElements: {-> i.hasNext() },
            nextElement: {-> i.next() }
        ]) as Enumeration
    }

    @Override
    String getProperty(String key) {
        return config.getProperty(key)
    }

    @Override
    Object get(Object key) {
        return getProperty(key?.toString())
    }

    @Override
    String getProperty(String key, String defaultValue) {
        return config.getProperty(key, defaultValue)
    }

    @Override
    Enumeration<Object> keys() {
        return (Enumeration<Object>)propertyNames()
    }

    @Override
    Set<Object> keySet() {
        return (Set<Object>)stringPropertyNames()
    }

    @Override
    String toString() {
        return config.toString()
    }
}
