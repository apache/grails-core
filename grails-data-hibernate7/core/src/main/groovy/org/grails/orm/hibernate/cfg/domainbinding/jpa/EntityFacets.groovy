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
 * The class-level decisions the domain binder makes for a root entity: the facets that end up on the Hibernate
 * {@code PersistentClass} and its table, and that {@link GrailsDomainGenerator} writes into the class annotations.
 *
 * <p>{@code schema}, {@code catalog} and {@code comment} are {@code null} when unset; {@code batchSize} is
 * {@code 0} when unset. {@code versioned} is whether the hierarchy has a version property: when it has none the binder
 * sets the optimistic lock style of the root to {@code NONE}, where Hibernate's own default is {@code VERSION}.</p>
 *
 * @since 9.0
 */
@CompileStatic
record EntityFacets(
    String jpaName,
    String tableName,
    String schema,
    String catalog,
    boolean dynamicInsert,
    boolean dynamicUpdate,
    int batchSize,
    String comment,
    boolean versioned) {
}
