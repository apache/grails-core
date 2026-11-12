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

package org.grails.datastore.mapping.config;

import java.util.Locale;

/**
 * How the {@code unique} constraint treats a {@code null} in a composite key, that is in the property it constrains
 * or in a property of its group. A database decides this for the unique index it keeps, and the validation can follow
 * it or choose its own rule.
 *
 * @since 9.0
 */
public enum UniqueNulls {

    /**
     * A {@code null} group value leaves its property out of the lookup, so it matches every row: {@code ('en', null)}
     * conflicts with {@code ('en', 'US')}. A {@code null} constrained value is not checked.
     */
    WILDCARD,

    /**
     * A row with a {@code null} in the constrained property or in its group is never a duplicate, as SQL reads a
     * unique predicate: {@code ('en', null)} twice is valid. The lookup is skipped.
     */
    SKIP,

    /**
     * A {@code null} is a value that matches only another {@code null}: {@code ('en', null)} conflicts with
     * {@code ('en', null)} and with nothing else, in the constrained property and in its group alike.
     */
    VALUE,

    /**
     * The reading of the database's own unique index: {@link #SKIP} where the index treats {@code null}s as distinct,
     * {@link #VALUE} where it compares them, so that the validation and the index agree. A datastore that does not
     * know, or keeps no such index, reads {@link #VALUE}. This is the default.
     */
    DATABASE;

    /**
     * Reads the setting or the constraint option, ignoring case.
     *
     * @param value an instance of this enum, or its name
     * @return the mode, or {@code null} for {@code null}
     * @throws IllegalArgumentException if the value names no mode
     */
    public static UniqueNulls of(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UniqueNulls mode) {
            return mode;
        }
        String name = value.toString().trim().toUpperCase(Locale.ROOT);
        for (UniqueNulls mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("The unique constraint reads nulls as 'wildcard', 'skip', 'value' or 'database', not '" + value + "'");
    }
}
