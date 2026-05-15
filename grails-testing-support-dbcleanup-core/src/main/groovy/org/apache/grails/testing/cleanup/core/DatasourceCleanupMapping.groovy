/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.testing.cleanup.core

import groovy.transform.CompileStatic

/**
 * Represents the parsed mapping entries from a {@link DatabaseCleanup} annotation's
 * {@code value()} attribute.
 *
 * <p>Each entry in the annotation value can be:</p>
 * <ul>
 *   <li>{@code 'datasourceName'} — clean the named datasource, auto-discover the cleaner
 *       via {@link DatabaseCleaner#supports(javax.sql.DataSource)}</li>
 *   <li>{@code 'datasourceName:databaseType'} — clean the named datasource using the
 *       cleaner that declares the specified {@link DatabaseCleaner#databaseType()}</li>
 * </ul>
 *
 * <p>If the annotation value is empty (the default), all data sources in the application
 * context are cleaned using auto-discovery.</p>
 *
 * <p>Examples:</p>
 * <pre>
 * // Clean all data sources (auto-discover cleaners)
 * &#64;DatabaseCleanup
 *
 * // Clean specific data sources (auto-discover cleaners)
 * &#64;DatabaseCleanup(['dataSource', 'dataSource_secondary'])
 *
 * // Clean specific data sources with explicit cleaner types
 * &#64;DatabaseCleanup(['dataSource:h2', 'dataSource_pg:postgresql'])
 *
 * // Mixed: some explicit, some auto-discovered
 * &#64;DatabaseCleanup(['dataSource:h2', 'dataSource_other'])
 * </pre>
 */
@CompileStatic
class DatasourceCleanupMapping {

    /**
     * A single parsed entry from the annotation value.
     */
    static class Entry {

        final String datasourceName
        final String databaseType

        Entry(String datasourceName, String databaseType) {
            this.datasourceName = datasourceName
            this.databaseType = databaseType
        }

        /**
         * @return {@code true} if this entry has an explicit database type mapping
         */
        boolean hasExplicitType() {
            databaseType
        }

        @Override
        String toString() {
            hasExplicitType() ?
                    "$datasourceName:$databaseType" :
                    datasourceName
        }
    }

    private final List<Entry> entries
    private final boolean cleanAll

    private DatasourceCleanupMapping(List<Entry> entries, boolean cleanAll) {
        this.entries = entries.asImmutable()
        this.cleanAll = cleanAll
    }

    /**
     * @return the parsed entries from the annotation value
     */
    List<Entry> getEntries() {
        entries
    }

    /**
     * @return {@code true} if no specific data sources were specified, meaning all
     *         datas ources in the application context should be cleaned
     */
    boolean isCleanAll() {
        cleanAll
    }

    /**
     * Parses the raw annotation value array into a {@link DatasourceCleanupMapping}.
     *
     * <p>Each entry is parsed as either {@code 'name'} (datasource name only, auto-discover
     * cleaner) or {@code 'name:type'} (datasource name with explicit database type).</p>
     *
     * @param annotationValues the raw string array from {@link DatabaseCleanup#value()}
     * @return the parsed mapping
     * @throws IllegalArgumentException if an entry has an empty datasource name or database type
     */
    static DatasourceCleanupMapping parse(String[] annotationValues) {
        if (!annotationValues) {
            return new DatasourceCleanupMapping([], true)
        }

        def entries = annotationValues.collect {
            def v = it?.trim()
            if (!v) {
                throw new IllegalArgumentException(
                        '@DatabaseCleanup contains a null or empty entry'
                )
            }

            def parts = v.split(':', 2)*.trim()
            if (parts.size() == 1) {
                // No colon — datasource name only, auto-discover cleaner
                return new Entry(parts[0], null)
            }

            def name = parts[0]
            def type = parts[1]
            if (!name) {
                throw new IllegalArgumentException(
                        "Invalid @DatabaseCleanup entry '$it': " +
                        'datasource name cannot be empty'
                )
            }
            if (!type) {
                throw new IllegalArgumentException(
                        "Invalid @DatabaseCleanup entry '$it': " +
                        "database type cannot be empty after ':'"
                )
            }

            new Entry(name, type)
        }

        new DatasourceCleanupMapping(entries, false)
    }
}
