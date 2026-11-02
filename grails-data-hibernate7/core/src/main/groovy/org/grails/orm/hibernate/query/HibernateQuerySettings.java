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
package org.grails.orm.hibernate.query;

import java.util.Map;

import org.hibernate.query.QueryFlushMode;

/**
 * The per-query settings a GORM query arguments map can carry for Hibernate: {@code fetchSize},
 * {@code timeout}, {@code readOnly} and {@code flushMode}.
 *
 * <p>Both query paths, HQL ({@link HqlQueryMethods}) and criteria ({@link HibernateQuery}), read the
 * arguments through this one type so a setting cannot be honoured by one path and dropped by the other.
 * A setting that is absent from the map is {@code null}, meaning "leave the Hibernate default".</p>
 *
 * @param fetchSize the JDBC fetch size, or {@code null}
 * @param timeout the query timeout in seconds, or {@code null}
 * @param readOnly whether loaded entities are read-only, or {@code null}
 * @param flushMode the flush mode, or {@code null}
 * @since 8.0.1
 */
public record HibernateQuerySettings(Integer fetchSize, Integer timeout, Boolean readOnly, QueryFlushMode flushMode) {

    /** Settings that leave every Hibernate default untouched. */
    public static final HibernateQuerySettings NONE = new HibernateQuerySettings(null, null, null, null);

    /**
     * Reads the settings from a query arguments map.
     *
     * @param args the arguments, may be {@code null}
     * @return the settings, never {@code null}
     * @throws IllegalArgumentException when a value cannot be converted; the message names the argument
     */
    public static HibernateQuerySettings from(Map<?, ?> args) {
        if (args == null || args.isEmpty()) {
            return NONE;
        }
        String fetchSizeKey = HibernateQueryArgument.FETCH_SIZE.value();
        String timeoutKey = HibernateQueryArgument.TIMEOUT.value();
        String readOnlyKey = HibernateQueryArgument.READ_ONLY.value();
        String flushModeKey = HibernateQueryArgument.FLUSH_MODE.value();
        return new HibernateQuerySettings(
                args.containsKey(fetchSizeKey) ? toInteger(fetchSizeKey, args.get(fetchSizeKey)) : null,
                args.containsKey(timeoutKey) ? toInteger(timeoutKey, args.get(timeoutKey)) : null,
                args.containsKey(readOnlyKey) ? HqlQueryMethods.toBoolean(args.get(readOnlyKey)) : null,
                args.containsKey(flushModeKey) ?
                        GrailsQueryFlushMode.mapToHibernateQueryFlushMode(args.get(flushModeKey)) :
                        null);
    }

    private static Integer toInteger(String argument, Object value) {
        try {
            return HqlQueryMethods.toInteger(value);
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Query argument '" + argument + "' must be a whole number but was '" + value + "'", e);
        }
    }
}
