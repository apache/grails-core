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

package example.extensions

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.query.api.Criteria

/**
 * Groovy extension module methods added to Criteria.
 *
 * Registered via META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule.
 */
@CompileStatic
class CriteriaBuilderExtensions {

    /**
     * Adds an equality restriction to the criteria only when the sanitized value is non-null.
     * For String values, the value is trimmed (when trim is true) and treated as absent when
     * empty or blank. Non-String values are passed through unchanged; null is treated as absent.
     *
     * @param self      the Hibernate Criteria instance
     * @param attribute the property name to restrict
     * @param value     the candidate value; null or blank strings are ignored
     * @param trim      whether to trim String values before the null check (default true)
     */
    static void eqIf(Criteria self, String attribute, Object value, boolean trim = true) {
        Object sanitized = sanitize(value, trim)
        if (sanitized != null) {
            self.eq attribute, sanitized
        }
    }

    private static Object sanitize(Object value, boolean trim) {
        if (value instanceof CharSequence) {
            CharSequence str = trim ? (value as String).trim() : (CharSequence) value
            return str ?: null
        }
        return value
    }
}
