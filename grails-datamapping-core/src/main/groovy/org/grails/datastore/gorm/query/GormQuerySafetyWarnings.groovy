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
package org.grails.datastore.gorm.query

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

import org.slf4j.Logger

@CompileStatic
final class GormQuerySafetyWarnings {

    private static final String GSTRING_VALUE_PLACEHOLDER = '${...}'
    private static final int MAX_WARNED_QUERY_SHAPES = 1000
    private static final Set<String> WARNED_GSTRING_QUERY_SHAPES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>())

    private GormQuerySafetyWarnings() {
    }

    static boolean warnIfGStringQuery(Logger logger, CharSequence query, String operation) {
        if (!(query instanceof GString) || ((GString) query).values.length == 0) {
            return false
        }

        if (!logger.warnEnabled) {
            return false
        }

        String queryShape = buildQueryShape((GString) query)
        String warningKey = "${operation}\n${queryShape}"
        synchronized (WARNED_GSTRING_QUERY_SHAPES) {
            if (WARNED_GSTRING_QUERY_SHAPES.size() >= MAX_WARNED_QUERY_SHAPES) {
                WARNED_GSTRING_QUERY_SHAPES.clear()
            }
            if (!WARNED_GSTRING_QUERY_SHAPES.add(warningKey)) {
                return false
            }
        }

        logger.warn('GString-interpolated query passed to [{}]. GORM binds interpolated values as query parameters, but explicit named parameters are recommended for query safety and readability. Query shape: [{}]', operation, queryShape)
        return true
    }

    private static String buildQueryShape(GString query) {
        StringBuilder queryShape = new StringBuilder()
        String[] strings = query.strings
        Object[] values = query.values
        for (int stringIndex = 0; stringIndex < strings.length; stringIndex++) {
            queryShape.append(strings[stringIndex])
            if (stringIndex < values.length) {
                queryShape.append(GSTRING_VALUE_PLACEHOLDER)
            }
        }
        return queryShape.toString()
    }
}
