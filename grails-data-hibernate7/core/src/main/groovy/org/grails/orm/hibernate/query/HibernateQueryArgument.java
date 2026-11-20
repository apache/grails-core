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

import org.grails.datastore.gorm.finders.DynamicFinder;

/**
 * Typed enum of all query argument keys and Hibernate config property keys used in the
 * Hibernate 7 datastore. String values are sourced from {@link DynamicFinder} for the
 * query arguments, eliminating the three duplicate sets of raw string constants that
 * previously existed across {@code HibernateQueryConstants}, {@code GrailsHibernateUtil},
 * and {@code DynamicFinder}.
 *
 * <p>Use {@link #value()} to obtain the string key for map lookups. {@link #toString()}
 * also returns the string value so instances can be used in string-interpolated contexts.
 *
 * @since 8.0
 */
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public enum HibernateQueryArgument {

    // ── pagination & execution ────────────────────────────────────────────────
    MAX(DynamicFinder.ARGUMENT_MAX),
    OFFSET(DynamicFinder.ARGUMENT_OFFSET),
    FETCH_SIZE(DynamicFinder.ARGUMENT_FETCH_SIZE),
    TIMEOUT(DynamicFinder.ARGUMENT_TIMEOUT),
    FLUSH_MODE(DynamicFinder.ARGUMENT_FLUSH_MODE),
    READ_ONLY(DynamicFinder.ARGUMENT_READ_ONLY),
    CACHE(DynamicFinder.ARGUMENT_CACHE),
    LOCK(DynamicFinder.ARGUMENT_LOCK),
    FETCH(DynamicFinder.ARGUMENT_FETCH),

    // ── sorting ───────────────────────────────────────────────────────────────
    SORT(DynamicFinder.ARGUMENT_SORT),
    ORDER(DynamicFinder.ARGUMENT_ORDER),
    IGNORE_CASE(DynamicFinder.ARGUMENT_IGNORE_CASE),
    ORDER_DESC(DynamicFinder.ORDER_DESC),
    ORDER_ASC(DynamicFinder.ORDER_ASC),
    EAGER("eager"),
    JOIN("join"),

    // ── HQL keywords ──────────────────────────────────────────────────────────
    // The reader of an HQL query uses HqlKeyword; these entries stay for anything that referenced them.
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_SELECT("select"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_FROM("from"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_WHERE("where"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_JOIN("join"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_LEFT("left"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_RIGHT("right"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_INNER("inner"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_OUTER("outer"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_GROUP("group"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_ORDER("order"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_HAVING("having"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_DISTINCT("distinct"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_ALL("all"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_AS("as"),
    /** @deprecated since 8.0.1, the HQL keywords live in {@code HqlKeyword}; this entry is unused and goes in 9.0 */
    @Deprecated(since = "8.0.1", forRemoval = true)
    HQL_NEW("new"),

    // ── Hibernate config properties ───────────────────────────────────────────
    CONFIG_CACHE_QUERIES("grails.hibernate.cache.queries"),
    CONFIG_OSIV_READONLY("grails.hibernate.osiv.readonly"),
    CONFIG_PASS_READONLY("grails.hibernate.pass.readonly");

    private final String value;

    HibernateQueryArgument(String value) {
        this.value = value;
    }

    /** Returns the string key used for map lookups and config property resolution. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
