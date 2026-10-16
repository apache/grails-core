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

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

/**
 * One column-bearing property of an embedded object, as the domain binder binds it for one owner: the property of the
 * embedded type, its path relative to the embedded property ({@code street}, or {@code zip.code} inside a nested
 * embedded type) and the column facets the owner states for it with {@code @AttributeOverride}.
 *
 * <p>{@code column} is {@code null} for a derived (formula) property, which has no column. {@code toOne} is set when the
 * property is a to-one association (its {@code column} is then the foreign key column): the owner states that column with
 * {@code @AssociationOverride} and the embeddable keeps the association's own facets.</p>
 *
 * @since 9.0
 */
@CompileStatic
record EmbeddedLeaf(
    String path,
    HibernatePersistentProperty property,
    ColumnFacets column,
    ToOneFacets toOne) {
}
