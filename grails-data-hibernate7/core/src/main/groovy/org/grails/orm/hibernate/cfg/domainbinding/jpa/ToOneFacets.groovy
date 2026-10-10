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
 * The decisions the domain binder makes for an association whose foreign key is a column of the owner's table (a
 * many-to-one, or a one-to-one that the binder binds as one): the associated entity, the fetching, the cascade and the
 * foreign key column. {@link GrailsDomainGenerator} writes them into {@code @ManyToOne}, {@code @JoinColumn},
 * {@code @Fetch}, {@code @NotFound} and {@code @Cascade}.
 *
 * <p>The same record describes the inverse side of a bidirectional one-to-one, which the binder binds as a Hibernate
 * {@code OneToOne} with no column of its own: {@code mappedBy} names the property of the other side that holds the foreign
 * key, {@code joinColumn} is {@code null} and {@code referencedEntity} is the entity name the binder gives the value (the
 * entity that declares the other side, which is the target or one of its superclasses). For a foreign key association both
 * are {@code null}.</p>
 *
 * <p>{@code target} is the GORM entity name of the associated entity; the generated field is typed with the class
 * generated for it. {@code fetchMode} is {@code JOIN} or {@code SELECT}: the binder's {@code DEFAULT} is a select.
 * {@code optional} is what the annotation states: Hibernate makes the join column NOT NULL for a non-optional
 * association, so it is {@code true} exactly when the join column is nullable (and for the inverse side of a one-to-one, which has no column).</p>
 *
 * <p>{@code joinColumns} are the foreign key columns in the order the binder binds them: one for an ordinary association, one
 * for each identifier property when the associated entity has a composite identifier, none for the inverse side of a
 * one-to-one. {@code referencedColumns} names, for a composite identifier, the column of the associated entity's key that each
 * foreign key column points at (the generated {@code @JoinColumn} states it); it is empty for an ordinary association, which
 * points at the single key column by default. {@code joinColumn} is the first of the foreign key columns.</p>
 *
 * @since 9.0
 */
@CompileStatic
record ToOneFacets(
    String target,
    boolean lazy,
    FetchMode fetchMode,
    boolean optional,
    boolean ignoreNotFound,
    CascadeFacets cascade,
    ColumnFacets joinColumn,
    String mappedBy,
    String referencedEntity,
    List<ColumnFacets> joinColumns,
    List<String> referencedColumns) {
}
