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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import groovy.transform.CompileStatic

/**
 * The kind of Hibernate collection the domain binder creates for a GORM collection property. The binder picks it from
 * the exact declared type of the property, and so does the generated field: {@code Set} is a set, {@code List} is an
 * indexed list, {@code Collection} is a bag and {@code Map} is a map. A {@code SortedSet} of entities is a sorted set; the
 * binder cannot bind a {@code SortedSet} of basic values.
 *
 * @since 9.0
 */
@CompileStatic
enum CollectionKind {

    SET(Set),
    LIST(List),
    BAG(Collection),
    MAP(Map),
    SORTED_SET(SortedSet)

    private final Class<?> javaType

    CollectionKind(Class<?> javaType) {
        this.javaType = javaType
    }

    Class<?> getJavaType() {
        return javaType
    }

    /** @return the kind for the exact declared type, or {@code null} when the binder has no collection for it */
    static CollectionKind of(Class<?> type) {
        return values().find { CollectionKind kind -> kind.javaType == type }
    }

    boolean isIndexed() {
        return this == LIST || this == MAP
    }

}
