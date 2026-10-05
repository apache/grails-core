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

import jakarta.persistence.Column
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.mapping.FilterConfiguration
import org.hibernate.mapping.PersistentClass

import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Describes the multi-tenant filter {@link GrailsDomainGenerator} writes for a multi-tenant entity. GORM only gives an
 * entity a tenant id in discriminator multi-tenancy mode, so this spec boots in that mode; the differential spec compares
 * the filter with the binder's on every multi-tenant domain class.
 */
class GrailsDomainGeneratorTenantSpec extends HibernateGormDatastoreSpec {

    List<StandardServiceRegistry> registries = []

    void setupSpec() {
        manager.grailsConfig = [
                'dataSource.url'                         : 'jdbc:h2:mem:grailsDB-generator-tenant;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                    : 'create-drop',
                'grails.gorm.multiTenancy.mode'          : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolver': new SystemPropertyTenantResolver()
        ]
        manager.registerDomainClasses(GenTenantBook, GenTenantNamed, GenTenantRoot, GenTenantLeaf, GenTenantJoinedRoot, GenTenantJoinedLeaf, GenTenantPlain,
                GenTenantTyped)
    }

    void cleanup() {
        registries.each { StandardServiceRegistryBuilder.destroy(it) }
        registries.clear()
    }

    void "the tenant id is an ordinary column and the entity gets the tenant filter"() {
        when:
        Class<?> generated = generate(GenTenantBook)

        then:
        generated.getDeclaredField('tenantId').getAnnotation(Column).name() == 'tenant_id'
        generated.getDeclaredField('tenantId').getAnnotation(Column).nullable()
        generated.getAnnotation(Filter).name() == 'tenantId'
        generated.getAnnotation(Filter).condition() == ':tenantId = tenant_id'
        generated.getAnnotation(Filter).deduceAliasInjectionPoints()
    }

    void "the facets of the filter are the ones the binder bound"() {
        given:
        GrailsDomainGenerator generator = newGenerator()
        PersistentClass bound = getPersistentEntity(GenTenantBook).persistentClass
        TenantFacets facets = generator.tenantFacets(entity(GenTenantBook))

        expect:
        facets.filterName() == 'tenantId'
        facets.condition() == bound.filters*.condition.first()
        facets.parameterType() == String
        bound.filters*.name == ['tenantId']
        getSessionFactory().getFilterDefinition('tenantId').parameterNames == ['tenantId'].toSet()
    }

    void "exactly one class of a call carries the filter definition, with the tenant id's type for its parameter"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll(GenTenantBook, GenTenantNamed, GenTenantPlain)
        List<Class<?>> defining = classes.values().findAll { it.isAnnotationPresent(FilterDef) }

        then:
        defining.size() == 1
        defining[0].getAnnotation(FilterDef).name() == 'tenantId'
        defining[0].getAnnotation(FilterDef).parameters()*.name() == ['tenantId']
        defining[0].getAnnotation(FilterDef).parameters()[0].type() == String
        defining[0].getAnnotation(FilterDef).defaultCondition() == ''
        !defining[0].getAnnotation(FilterDef).autoEnabled()
        classes[entity(GenTenantBook)].isAnnotationPresent(Filter)
        classes[entity(GenTenantNamed)].isAnnotationPresent(Filter)
        !classes[entity(GenTenantPlain)].isAnnotationPresent(Filter)
    }

    void "the filter condition names the default column of the tenant id even when the mapping names another"() {
        given:
        Class<?> generated = generate(GenTenantNamed)

        expect: "the binder builds the condition from the default column name, so a mapped column makes the filter point at a column that does not exist"
        generated.getDeclaredField('companyId').getAnnotation(Column).name() == 'owner_tenant'
        generated.getAnnotation(Filter).condition() == ':tenantId = company_id'
        getPersistentEntity(GenTenantNamed).persistentClass.filters*.condition == [':tenantId = company_id']
    }

    void "a single-table subclass has no filter of its own and neither has a joined subclass that inherits the tenant id"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll(GenTenantRoot, GenTenantLeaf, GenTenantJoinedRoot, GenTenantJoinedLeaf)

        then:
        classes[entity(GenTenantRoot)].isAnnotationPresent(Filter)
        !classes[entity(GenTenantLeaf)].isAnnotationPresent(Filter)
        classes[entity(GenTenantJoinedRoot)].isAnnotationPresent(Filter)
        !classes[entity(GenTenantJoinedLeaf)].isAnnotationPresent(Filter)
        classes.values().count { it.isAnnotationPresent(FilterDef) } == 1
        classes[entity(GenTenantLeaf)].declaredFields*.name == ['extra']
    }

    void "an entity that is not multi-tenant has no filter"() {
        expect:
        newGenerator().tenantFacets(entity(GenTenantPlain)) == null
        !generate(GenTenantPlain).isAnnotationPresent(Filter)
        !generate(GenTenantPlain).isAnnotationPresent(FilterDef)
    }

    void "Hibernate's annotation binder reads the filter and its definition as the binder builds them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll(GenTenantBook, GenTenantNamed, GenTenantPlain)

        when:
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass book = metadata.getEntityBinding(classes[entity(GenTenantBook)].name)
        FilterDefinition definition = metadata.getFilterDefinition('tenantId')

        then:
        book.filters*.name == ['tenantId']
        book.filters*.condition == getPersistentEntity(GenTenantBook).persistentClass.filters*.condition
        book.filters.first().useAutoAliasInjection()
        metadata.getEntityBinding(classes[entity(GenTenantPlain)].name).filters.isEmpty()
        definition.parameterNames == ['tenantId'].toSet()
        definition.getParameterJdbcMapping('tenantId').javaTypeDescriptor.javaTypeClass == String
        !definition.autoEnabled
        !definition.appliedToLoadByKey

        and: "the binder registered the same definition"
        getSessionFactory().getFilterDefinition('tenantId').getParameterJdbcMapping('tenantId').javaTypeDescriptor.javaTypeClass == String
    }

    void "a tenant id with a mapped type is rejected by name, because the filter parameter would need the same type"() {
        when:
        generate(GenTenantTyped)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('The tenant id [companyId] of [' + GenTenantTyped.name + ']')
    }

    private Metadata annotationMetadata(Collection<Class<?>> classes) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(
                new BootstrapServiceRegistryBuilder().applyClassLoader(classes.first().classLoader).build())
                .applySetting('hibernate.dialect', H2Dialect.name)
                .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-tenant;DB_CLOSE_DELAY=-1')
                .build()
        registries << registry
        MetadataSources sources = new MetadataSources(registry)
        classes.each { sources.addAnnotatedClass(it) }
        return sources.buildMetadata()
    }

    private Map<GrailsHibernatePersistentEntity, Class<?>> generateAll(Class<?>... domainClasses) {
        return newGenerator().generateAll(domainClasses.collect { entity(it) }, getClass().classLoader)
    }

    private Class<?> generate(Class<?> domainClass) {
        return newGenerator().generate(entity(domainClass), getClass().classLoader)
    }

    private GrailsHibernatePersistentEntity entity(Class<?> domainClass) {
        return getPersistentEntity(domainClass)
    }

    private GrailsDomainGenerator newGenerator() {
        def naming = getGrailsDomainBinder().getNamingStrategy()
        return new GrailsDomainGenerator(
                naming,
                new ColumnNameForPropertyAndPathFetcher(naming, new DefaultColumnNameFetcher(naming), new BackticksRemover()),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(new H2Dialect()),
                getSessionFactory().typeConfiguration)
    }
}

@Entity
class GenTenantBook implements MultiTenant<GenTenantBook> {

    Long id
    String title
    String tenantId
}

@Entity
class GenTenantNamed implements MultiTenant<GenTenantNamed> {

    String name
    String companyId

    static mapping = {
        tenantId name: 'companyId'
        columns {
            companyId column: 'owner_tenant'
        }
    }
}

@Entity
class GenTenantRoot implements MultiTenant<GenTenantRoot> {

    String name
    String tenantId
}

@Entity
class GenTenantLeaf extends GenTenantRoot {

    String extra
}

@Entity
class GenTenantJoinedRoot implements MultiTenant<GenTenantJoinedRoot> {

    String name
    String tenantId

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class GenTenantJoinedLeaf extends GenTenantJoinedRoot {

    String extra
}

@Entity
class GenTenantPlain {

    String name
}

@Entity
class GenTenantTyped implements MultiTenant<GenTenantTyped> {

    String name
    String companyId

    static mapping = {
        tenantId name: 'companyId'
        columns {
            companyId type: GenUpperType
        }
    }
}
