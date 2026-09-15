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
package grails.plugin.geb;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Overrides a system property for a custom browser factory on its calling thread.
 * Property lookup must stay in Java: Groovy call-site initialization itself reads
 * system properties and would recursively enter a Groovy-backed property lookup.
 */
final class ThreadLocalPropertyScope {

    private static final ThreadLocal<Map<String, String>> OVERRIDES = ThreadLocal.withInitial(HashMap::new);
    private static boolean installed;

    private ThreadLocalPropertyScope() {
    }

    static <T> T withProperty(String key, String value, Supplier<T> body) {
        installProperties();
        Map<String, String> overrides = OVERRIDES.get();
        String previous = overrides.put(key, value);
        try {
            return body.get();
        }
        finally {
            if (previous == null) {
                overrides.remove(key);
            }
            else {
                overrides.put(key, previous);
            }
            if (overrides.isEmpty()) {
                OVERRIDES.remove();
            }
        }
    }

    private static synchronized void installProperties() {
        if (!installed) {
            Properties properties = new InterceptingProperties();
            properties.putAll(System.getProperties());
            System.setProperties(properties);
            installed = true;
        }
    }

    private static final class InterceptingProperties extends Properties {
        private static final long serialVersionUID = 1L;

        @Override
        public String getProperty(String key) {
            String value = OVERRIDES.get().get(key);
            return value != null ? value : super.getProperty(key);
        }
    }
}
