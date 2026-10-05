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
 * <p>{@code target} is the GORM entity name of the associated entity; the generated field is typed with the class
 * generated for it. {@code fetchMode} is {@code JOIN} or {@code SELECT}: the binder's {@code DEFAULT} is a select.
 * {@code optional} is what the annotation states: Hibernate makes the join column NOT NULL for a non-optional
 * association, so it is {@code true} exactly when the join column is nullable.</p>
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
    ColumnFacets joinColumn) {
}
