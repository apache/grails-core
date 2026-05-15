/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding.binder

import org.hibernate.mapping.SingleTableSubclass

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.RootClass
import org.grails.datastore.mapping.core.connections.ConnectionSource

class SubClassBinderSpec extends HibernateGormDatastoreSpec {

    SubClassBinder binder
    SubclassMappingBinder subclassMappingBinder
    MultiTenantFilterBinder multiTenantFilterBinder
    MappingCacheHolder mappingCacheHolder
    MetadataBuildingContext metadataBuildingContext

    void setup() {
        def gdb = getGrailsDomainBinder()
        
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        mappingCacheHolder = gdb.getMappingCacheHolder()
        subclassMappingBinder = Mock(SubclassMappingBinder)
        multiTenantFilterBinder = Mock(MultiTenantFilterBinder)
        
        binder = new SubClassBinder(
                subclassMappingBinder,
                multiTenantFilterBinder,
                ConnectionSource.DEFAULT,
        )
    }

    def 'test bindSubClass with no children'() {
        given:
        def subEntity = Mock(HibernatePersistentEntity)
        subEntity.getName() >> 'Child'
        subEntity.getChildEntities(ConnectionSource.DEFAULT) >> []
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName('Parent')
        rootClass.setJpaEntityName('Parent')
        def subClass = new SingleTableSubclass(rootClass, metadataBuildingContext)
        subClass.setEntityName('Child')
        subClass.setJpaEntityName('Child')

        when:
        def results = binder.bindSubClass(subEntity, rootClass)

        then:
        1 * subclassMappingBinder.createSubclassMapping(subEntity, rootClass) >> subClass
        1 * multiTenantFilterBinder.bind(subEntity, subClass)
        results == [subClass]
    }

    def 'test bindSubClass with children'() {
        given:
        def subEntity = Mock(HibernatePersistentEntity)
        def grandChildEntity = Mock(HibernatePersistentEntity)
        subEntity.getName() >> 'Child'
        grandChildEntity.getName() >> 'GrandChild'
        subEntity.getChildEntities(ConnectionSource.DEFAULT) >> [grandChildEntity]
        grandChildEntity.getChildEntities(ConnectionSource.DEFAULT) >> []

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName('Parent')
        rootClass.setJpaEntityName('Parent')

        def subClass = new org.hibernate.mapping.SingleTableSubclass(rootClass, metadataBuildingContext)
        subClass.setEntityName('Child')
        subClass.setJpaEntityName('Child')
        def grandChildSubClass = new org.hibernate.mapping.SingleTableSubclass(subClass, metadataBuildingContext)
        grandChildSubClass.setEntityName('GrandChild')
        grandChildSubClass.setJpaEntityName('GrandChild')

        when:
        def results = binder.bindSubClass(subEntity, rootClass)

        then:
        1 * subclassMappingBinder.createSubclassMapping(subEntity, rootClass) >> subClass
        1 * subclassMappingBinder.createSubclassMapping(grandChildEntity, subClass) >> grandChildSubClass
        2 * multiTenantFilterBinder.bind(_, _)
        results == [subClass, grandChildSubClass]
    }
}
