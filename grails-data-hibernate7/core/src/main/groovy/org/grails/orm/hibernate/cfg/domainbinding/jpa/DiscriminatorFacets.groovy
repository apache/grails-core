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
import jakarta.persistence.DiscriminatorType

/**
 * The discriminator the domain binder gives the root of a table-per-hierarchy tree: a column or a formula, the type
 * it is read as, and whether the discriminator is written on insert.
 *
 * <p>Exactly one of {@code column} and {@code formula} is set. {@code length} and {@code sqlType} are {@code null}
 * when the mapping does not state them. {@code typeName} is the Hibernate type name the binder puts on the
 * discriminator value ({@code string} unless the mapping says otherwise) and {@code type} is the JPA discriminator
 * type that stands for it. {@code precision} and {@code scale} are the ones the mapping gives the column: no annotation states
 * them on a discriminator, and they change nothing in the DDL of a string, integer or character column, but the binder puts them
 * on the column of the model, so the aligner does the same.</p>
 *
 * @since 9.0
 */
@CompileStatic
record DiscriminatorFacets(
    String column,
    String formula,
    String typeName,
    DiscriminatorType type,
    Integer length,
    String sqlType,
    boolean insertable,
    Integer precision,
    Integer scale) {
}
