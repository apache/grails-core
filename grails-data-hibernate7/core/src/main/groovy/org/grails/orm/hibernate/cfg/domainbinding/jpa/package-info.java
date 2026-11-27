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
/**
 * Native domain binding: Hibernate's own annotation binder binds the GORM domain classes, from classes generated out of
 * the GORM mapping. The application's real domain classes stay what Hibernate persists, loads and proxies.
 *
 * <p>Where to start, in the order the binding runs:</p>
 * <ol>
 *   <li>{@code HibernateMappingContextConfiguration} creates a {@link GeneratedDomainClassBinder} and the
 *   {@link GormMappingContributor} that hands the entities to it.</li>
 *   <li>{@link GeneratedDomainClassBinder#contribute} is the entry point of the binding: it has
 *   {@link GrailsDomainGenerator#generateAll} generate an annotated class for every entity and hands those classes to
 *   Hibernate's annotation binder.</li>
 *   <li>{@link GrailsDomainGenerator} holds every mapping decision: it reads a {@code GrailsHibernatePersistentEntity}
 *   into the records of {@link org.grails.orm.hibernate.cfg.domainbinding.jpa.facets}, which say what to state, and
 *   writes them as JPA and Hibernate annotations with ByteBuddy. {@code supports} and {@code unsupportedReason} say
 *   what it refuses, and why, before Hibernate boots.</li>
 *   <li>{@link GeneratedDomainClassLoader} resolves a domain class name to the generated class while Hibernate binds the
 *   mappings. {@link GeneratedDomainClassLoaderService} switches the same names to the real classes once the metadata is
 *   complete, from {@link GeneratedDomainClassBinder#getSessionFactoryBuilder}, which also points the bound entities at
 *   the real classes, their accessors and GORM's identifier generators.</li>
 *   <li>{@link GrailsIdGenerator} marks a generated identifier with the GORM generator the binder installs on it.</li>
 * </ol>
 *
 * <p>The tests under {@code grails.gorm.tests.generated} cover the behaviour. The two differential specs of the test
 * package of this package compare what the generator states with what the classic binder of Grails 8 bound, recorded
 * under {@code src/test/resources/classic-oracle}.</p>
 *
 * @since 9.0
 */
package org.grails.orm.hibernate.cfg.domainbinding.jpa;
