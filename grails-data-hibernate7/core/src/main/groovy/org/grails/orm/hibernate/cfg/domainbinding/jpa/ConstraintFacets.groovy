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
 * The indexes and multi-column unique keys the domain binder puts on one table, in the order it creates them, with the
 * names and column order it gives them. {@link GrailsDomainGenerator} writes them into
 * {@code @Table(indexes, uniqueConstraints)}.
 *
 * <p>{@code primaryKeyOrder} is the order of the primary key's columns when a unique key over exactly those columns is created
 * before the primary key, which Hibernate then drops after taking its column order for the primary key ({@code null} when there is
 * no such key).</p>
 *
 * <p>The indexes come from {@code index:} on a column ({@code IndexBinder}), the unique keys from {@code unique:} with
 * a group of properties ({@code CreateKeyForProps}). A plain {@code unique: true} is a unique column, not a unique key.</p>
 *
 * @since 9.0
 */
@CompileStatic
record ConstraintFacets(
    List<IndexFacets> indexes,
    List<UniqueKeyFacets> uniqueKeys,
    List<String> primaryKeyOrder) {
}
