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
 * The decisions the domain binder makes for a collection of entities: the associated entity, how the collection is
 * mapped (by the other side's foreign key, by a foreign key column that the owner manages, or through a join table), its
 * key, element and index columns, its fetching, cascade and ordering. {@link GrailsDomainGenerator} writes them into
 * {@code @OneToMany}, {@code @ManyToMany}, {@code @JoinColumn}, {@code @JoinTable}, {@code @OrderColumn},
 * {@code @OrderBy}, {@code @Fetch}, {@code @BatchSize}, {@code @Cache}, {@code @Cascade} and {@code @Filter}.
 *
 * <p>Exactly one of three shapes: {@code mappedBy} names the property of the target that holds the foreign key (the
 * collection is inverse, no table of its own); {@code mappedBy} and {@code tableName} are both {@code null} (an indexed list
 * the owner manages through the foreign key column {@code key} in the target's table); {@code tableName} is set (a join
 * table, with {@code key} pointing at the owner and {@code element} at the target).</p>
 *
 * <p>{@code index} is {@code null} unless the collection is an indexed list, {@code cacheUsage} is {@code null} when the
 * collection is not cached, {@code batchSize} is {@code 0} when unset, {@code orderProperty} and {@code orderDirection} are
 * {@code null} when the collection is not ordered, and {@code tenantCondition} is {@code null} unless the target is
 * multi-tenant. {@code fetchMode} is {@code JOIN} or {@code SELECT}: the binder's {@code DEFAULT} is a select.</p>
 *
 * @since 9.0
 */
@CompileStatic
record ToManyFacets(
    CollectionKind kind,
    String target,
    String mappedBy,
    String tableName,
    String schema,
    String catalog,
    ColumnFacets key,
    ColumnFacets element,
    ColumnFacets index,
    boolean lazy,
    FetchMode fetchMode,
    int batchSize,
    String cacheUsage,
    CascadeFacets cascade,
    String orderProperty,
    String orderDirection,
    String tenantCondition) {
}
