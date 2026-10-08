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
import org.hibernate.FetchMode

/**
 * The decisions the domain binder makes for a collection of basic values or enums: the collection table, the key column
 * that points back at the owner, the column that holds the element, the index column of a list or the key column of a
 * map, and how the collection is fetched. {@link GrailsDomainGenerator} writes them into {@code @ElementCollection},
 * {@code @CollectionTable}, {@code @Column}, {@code @OrderColumn}, {@code @MapKeyColumn}, {@code @Fetch},
 * {@code @BatchSize} and {@code @Cache}.
 *
 * <p>{@code schema} and {@code catalog} are {@code null} when unset, {@code index} is {@code null} unless the collection
 * is indexed, {@code cacheUsage} is {@code null} when the collection is not cached and {@code batchSize} is {@code 0}
 * when unset. {@code fetchMode} is {@code JOIN} or {@code SELECT}: the binder's {@code DEFAULT} is a select.</p>
 *
 * <p>{@code keys} are the key columns in the order the binder binds them: one, or one for each identifier property when the owner has
 * a composite identifier; {@code referencedKeys} names the column of the owner's key each one points at, and is empty unless the
 * owner has a composite identifier. {@code key} is the first of the key columns.</p>
 *
 * <p>{@code indexType} is the type the binder gives the index column of a list or the key column of a map when the mapping types it
 * ({@code indexColumn: [type: 'long']}), independently of the declared key class: the Java type and the JDBC type of the registered
 * type of that name. It is {@code null} when the mapping states none, or the type is the default (an integer for a list, a string for
 * a map). A list's index stays an integer in Java whatever its JDBC type; the key of a map is typed with the facets' Java type.</p>
 *
 * <p>{@code extraLazy} is an explicit {@code lazy: true}: the binder makes the collection extra-lazy, which Hibernate's annotation
 * binder cannot state, so {@code GeneratedDomainClassBinder} sets it on the collection after binding.</p>
 *
 * @since 9.0
 */
@CompileStatic
record CollectionFacets(
    CollectionKind kind,
    String tableName,
    String schema,
    String catalog,
    ColumnFacets key,
    ColumnFacets element,
    ColumnFacets index,
    boolean lazy,
    boolean extraLazy,
    FetchMode fetchMode,
    int batchSize,
    String cacheUsage,
    List<ColumnFacets> keys,
    List<String> referencedKeys,
    TypeFacets indexType) {
}
