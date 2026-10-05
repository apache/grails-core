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

import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * What the specs of {@link GrailsDomainGenerator} share: a generator wired like the domain binder, generation of a group
 * of entities, and Hibernate's own annotation binder over the generated classes, so a spec can compare the generated
 * class with what the domain binder bound.
 */
abstract class GrailsDomainGeneratorSupport extends HibernateGormDatastoreSpec {

    private final List<StandardServiceRegistry> registries = []

    void cleanup() {
        registries.each { StandardServiceRegistryBuilder.destroy(it) }
        registries.clear()
    }

    protected GrailsDomainGenerator newGenerator() {
        def naming = getGrailsDomainBinder().getNamingStrategy()
        return new GrailsDomainGenerator(
                naming,
                new ColumnNameForPropertyAndPathFetcher(naming, new DefaultColumnNameFetcher(naming), new BackticksRemover()),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(new H2Dialect()),
                getSessionFactory().typeConfiguration)
    }

    protected GrailsHibernatePersistentEntity entity(Class<?> domainClass) {
        return getPersistentEntity(domainClass)
    }

    protected Map<GrailsHibernatePersistentEntity, Class<?>> generateGroup(Class<?>... domainClasses) {
        return newGenerator().generateAll(domainClasses.collect { entity(it) }, getClass().classLoader)
    }

    protected Metadata annotationMetadata(Collection<Class<?>> classes) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(
                new BootstrapServiceRegistryBuilder().applyClassLoader(classes.first().classLoader).build())
                .applySetting('hibernate.dialect', H2Dialect.name)
                .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-support;DB_CLOSE_DELAY=-1')
                .build()
        registries << registry
        MetadataSources sources = new MetadataSources(registry)
        classes.each { sources.addAnnotatedClass(it) }
        return sources.buildMetadata()
    }
}
