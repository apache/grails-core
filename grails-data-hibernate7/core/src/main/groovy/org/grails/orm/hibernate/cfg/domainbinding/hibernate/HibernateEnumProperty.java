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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

/**
 * Marker interface for Hibernate persistent properties whose Java type is an enum.
 *
 * <p>Two concrete subtypes exist, corresponding to the two creation paths in {@link
 * HibernateMappingFactory}:
 *
 * <ul>
 *   <li>{@link HibernateSimpleEnumProperty} — plain enum with no custom type marshaller
 *   <li>{@link HibernateCustomEnumProperty} — enum backed by a custom type marshaller
 * </ul>
 *
 * <p>Use {@code instanceof HibernateEnumProperty} instead of {@code isEnumType()} to branch on
 * enum properties at binding time.
 */
public interface HibernateEnumProperty extends HibernatePersistentProperty {}
