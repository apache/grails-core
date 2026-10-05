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
 * The column-level decisions the domain binder makes for one basic property: the facets that end up on a
 * Hibernate {@code Column} and that {@link GrailsDomainGenerator} writes into {@code @Column}.
 *
 * <p>{@code length}, {@code precision}, {@code scale} and {@code sqlType} are {@code null} when nothing set them.</p>
 *
 * @since 9.0
 */
@CompileStatic
record ColumnFacets(
    String name,
    boolean nullable,
    boolean unique,
    boolean insertable,
    boolean updatable,
    Integer length,
    Integer precision,
    Integer scale,
    String sqlType) {
}
