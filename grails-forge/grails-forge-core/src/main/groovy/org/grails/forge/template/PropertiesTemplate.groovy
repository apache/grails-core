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
package org.grails.forge.template

import groovy.transform.CompileStatic

@CompileStatic
class PropertiesTemplate implements Template {

    private final String path
    private final Properties properties

    PropertiesTemplate(String path, Map<String, Object> config) {
        this.path = path
        this.properties = transform(new LinkedProperties(), '', config)
    }

    @Override
    String getPath() {
        return path
    }

    @Override
    void write(OutputStream outputStream) throws IOException {
        properties.store(outputStream, null)
    }

    private Properties transform(Properties finalConfig, String prefix, Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            transform(finalConfig, prefix + entry.getKey(), entry.getValue())
        }
        return finalConfig
    }

    private void transform(Properties finalConfig, String prefix, Object value) {
        if (value instanceof Map) {
            transform(finalConfig, prefix + '.', (Map<String, Object>) value)
        } else if (value instanceof List) {
            List list = (List) value
            for (int i = 0; i < list.size(); i++) {
                transform(finalConfig, prefix + '[' + i + ']', list.get(i))
            }
        } else {
            finalConfig.put(prefix, value.toString())
        }
    }

    class LinkedProperties extends Properties {

        private final HashSet<Object> keys = new LinkedHashSet<>()

        Iterable<Object> orderedKeys() {
            return Collections.list(keys())
        }

        Enumeration<Object> keys() {
            return Collections.enumeration(keys)
        }

        Object put(Object key, Object value) {
            keys.add(key)
            return super.put(key, value)
        }
    }
}
