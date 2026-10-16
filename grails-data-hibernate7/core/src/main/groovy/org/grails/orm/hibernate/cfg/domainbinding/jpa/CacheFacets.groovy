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
 * The second-level cache the domain binder configures for the root of a hierarchy: the concurrency strategy, whether lazy
 * properties are cached with the entity, and whether the class is mutable (the binder makes a {@code read-only} cache
 * immutable). {@link GrailsDomainGenerator} writes them as {@code @Cacheable}, {@code @Cache(usage, includeLazy)} and
 * {@code @Immutable}.
 *
 * <p>{@code usage} is the strategy as the mapping names it ({@code read-only}, {@code read-write},
 * {@code nonstrict-read-write} or {@code transactional}).</p>
 *
 * @since 9.0
 */
@CompileStatic
record CacheFacets(
    String usage,
    boolean includeLazy,
    boolean mutable) {
}
