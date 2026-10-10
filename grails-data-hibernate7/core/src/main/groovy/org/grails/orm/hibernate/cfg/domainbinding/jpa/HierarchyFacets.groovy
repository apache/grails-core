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
import jakarta.persistence.InheritanceType

/**
 * The inheritance decisions the domain binder makes for one entity: where it sits in its hierarchy, whether it has
 * a table of its own, and what tells its rows apart.
 *
 * <p>{@code strategy} is {@code null} for an entity that is not part of a hierarchy (a root with no subclasses).
 * {@code superclass} is the entity name of the direct superclass, {@code null} for a root. {@code discriminator}
 * is only set for the root of a single-table hierarchy that has subclasses, {@code discriminatorValue} only for the
 * entities of a single-table hierarchy, and {@code keyColumn} only for a joined subclass.</p>
 *
 * @since 9.0
 */
@CompileStatic
record HierarchyFacets(
    InheritanceType strategy,
    String superclass,
    boolean abstractClass,
    boolean abstractTable,
    boolean ownsTable,
    String discriminatorValue,
    DiscriminatorFacets discriminator,
    String keyColumn) {
}
