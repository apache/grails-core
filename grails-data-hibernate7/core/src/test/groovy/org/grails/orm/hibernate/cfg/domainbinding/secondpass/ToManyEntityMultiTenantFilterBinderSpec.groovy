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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.hibernate.mapping.Bag
import spock.lang.Subject

class ToManyEntityMultiTenantFilterBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    ToManyEntityMultiTenantFilterBinder binder

    void setupSpec() {
        manager.addAllDomainClasses([
            CMTBBidirectionalOwner,
            CMTBBidirectionalItem,
            CMTBUnidirectionalOwner,
            CMTBUnidirectionalItem,
            CMTBNonTenantOwner,
            CMTBNonTenantItem,
            CMTBManyToManyOwner,
            CMTBManyToManyItem,
        ])
        manager.grailsConfig = [
            "grails.gorm.multiTenancy.mode"               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
            "grails.gorm.multiTenancy.tenantResolverClass": SystemPropertyTenantResolver,
        ]
    }

    void setup() {
        def ns = getGrailsDomainBinder().getNamingStrategy()
        binder = new ToManyEntityMultiTenantFilterBinder(new DefaultColumnNameFetcher(ns, new BackticksRemover()))
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    def "bind adds collection filter for bidirectional one-to-many to multi-tenant entity"() {
        given:
        def property = propertyFor(CMTBBidirectionalOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY }
        collection.getManyToManyFilters().isEmpty()
    }

    def "bind adds manyToMany filter for unidirectional one-to-many to multi-tenant entity"() {
        given:
        def property = propertyFor(CMTBUnidirectionalOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getManyToManyFilters().any { it.getName() == GormProperties.TENANT_IDENTITY }
        collection.getFilters().isEmpty()
    }

    def "bind does not add filter for ManyToMany even when associated entity is multi-tenant"() {
        given:
        def property = propertyFor(CMTBManyToManyOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getFilters().isEmpty()
        collection.getManyToManyFilters().isEmpty()
    }

    def "bind does not add filter when associated entity is not multi-tenant"() {
        given:
        def property = propertyFor(CMTBNonTenantOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getFilters().isEmpty()
        collection.getManyToManyFilters().isEmpty()
    }

    def "bind does nothing when associated entity is null (partially-resolved association)"() {
        given:
        def property = Stub(HibernateToManyEntityProperty) {
            getHibernateAssociatedEntity() >> null
            isOneToMany() >> true
        }

        when:
        binder.bind(property)

        then:
        noExceptionThrown()
    }
}

@Entity
class CMTBBidirectionalOwner {
    Long id
    static hasMany = [items: CMTBBidirectionalItem]
}

@Entity
class CMTBBidirectionalItem implements MultiTenant<CMTBBidirectionalItem> {
    Long id
    Long tenantId
    CMTBBidirectionalOwner owner
    static belongsTo = [owner: CMTBBidirectionalOwner]
}

@Entity
class CMTBUnidirectionalOwner {
    Long id
    static hasMany = [items: CMTBUnidirectionalItem]
}

@Entity
class CMTBUnidirectionalItem implements MultiTenant<CMTBUnidirectionalItem> {
    Long id
    Long tenantId
}

@Entity
class CMTBNonTenantOwner {
    Long id
    static hasMany = [items: CMTBNonTenantItem]
}

@Entity
class CMTBNonTenantItem {
    Long id
    String name
}

@Entity
class CMTBManyToManyOwner {
    Long id
    static hasMany = [items: CMTBManyToManyItem]
}

@Entity
class CMTBManyToManyItem implements MultiTenant<CMTBManyToManyItem> {
    Long id
    Long tenantId
    static hasMany = [owners: CMTBManyToManyOwner]
}
