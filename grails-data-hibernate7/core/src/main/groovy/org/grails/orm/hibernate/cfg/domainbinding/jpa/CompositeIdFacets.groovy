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
 * The composite identifier the domain binder binds for a root entity ({@code id composite: [...]}): the parts in the order the
 * mapping names them, each with its column facets (a simple property) or its foreign key column facets and association facets
 * (a many-to-one part). The binder builds one embedded identifier component from them, whose columns form the primary key and so
 * are never null. {@link GrailsDomainGenerator} writes them into a generated {@code @Embeddable} that the entity uses as its
 * {@code @EmbeddedId}.
 *
 * <p>{@code fieldName} is the name of the {@code @EmbeddedId} field of the generated class: the binder's identifier has no
 * property name, so any name that no property of the entity uses will do.</p>
 *
 * @since 9.0
 */
@CompileStatic
record CompositeIdFacets(
    String fieldName,
    List<EmbeddedLeaf> parts) {
}
