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

package org.grails.orm.hibernate.cfg.domainbinding.binder


import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.RootClass
import org.grails.datastore.mapping.core.connections.ConnectionSource

class RootBinderSpec extends HibernateGormDatastoreSpec {

    RootBinder binder
    MultiTenantFilterBinder multiTenantFilterBinder
    SubClassBinder subClassBinder
    RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder
    DiscriminatorPropertyBinder discriminatorPropertyBinder
    MetadataBuildingContext metadataBuildingContext
    PersistentEntityNamingStrategy namingStrategy
    def sharedCollector
    org.grails.orm.hibernate.cfg.MappingCacheHolder mappingCacheHolder

    void setup() {
        def gdb = getGrailsDomainBinder()
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        namingStrategy = gdb.getNamingStrategy()
        sharedCollector = getCollector()
        mappingCacheHolder = Mock(org.grails.orm.hibernate.cfg.MappingCacheHolder)

        multiTenantFilterBinder = Mock(MultiTenantFilterBinder)
        subClassBinder = Mock(SubClassBinder)
        rootPersistentClassCommonValuesBinder = Mock(RootPersistentClassCommonValuesBinder)
        discriminatorPropertyBinder = Mock(DiscriminatorPropertyBinder)

        binder = new RootBinder(
                ConnectionSource.DEFAULT,
                multiTenantFilterBinder,
                subClassBinder,
                rootPersistentClassCommonValuesBinder,
                discriminatorPropertyBinder,
                sharedCollector,
                mappingCacheHolder
        )
    }

    def "test bindRoot with no children"() {
        given:
        def entity = Mock(HibernatePersistentEntity)
        entity.getName() >> "Parent"
        entity.getChildEntities(ConnectionSource.DEFAULT) >> []
        entity.getMappedForm() >> new Mapping()
        
        def mappings = sharedCollector
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")

        when:
        binder.bindRoot(entity)

        then:
        1 * rootPersistentClassCommonValuesBinder.bindRoot(entity) >> rootClass
        0 * discriminatorPropertyBinder.bindDiscriminatorProperty(_)
        0 * subClassBinder.bindSubClass(_, _)
        1 * multiTenantFilterBinder.bind(entity, rootClass)
        mappings.getEntityBinding("Parent") == rootClass
    }

    def "test bindRoot with children and table-per-hierarchy"() {
        given:
        def entity = Mock(HibernatePersistentEntity)
        def childEntity = Mock(HibernatePersistentEntity)
        entity.getName() >> "Parent"
        entity.getChildEntities(ConnectionSource.DEFAULT) >> [childEntity]
        def mapping = new Mapping()
        mapping.setTablePerHierarchy(true)
        entity.getMappedForm() >> mapping
        entity.isTablePerHierarchy() >> true
        
        def mappings = sharedCollector
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")

        when:
        binder.bindRoot(entity)

        then:
        1 * rootPersistentClassCommonValuesBinder.bindRoot(entity) >> rootClass
        1 * mappingCacheHolder.cacheMapping(childEntity)
        1 * discriminatorPropertyBinder.bindDiscriminatorProperty(rootClass)
        1 * subClassBinder.bindSubClass(childEntity, rootClass) >> []
        1 * multiTenantFilterBinder.bind(entity, rootClass)
        mappings.getEntityBinding("Parent") == rootClass
    }

    def "test bindRoot already mapped"() {
        given:
        def entity = Mock(HibernatePersistentEntity)
        entity.getName() >> "Parent"
        def mappings = sharedCollector
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")
        mappings.addEntityBinding(rootClass)

        when:
        binder.bindRoot(entity)

        then:
        0 * rootPersistentClassCommonValuesBinder.bindRoot(_)
    }
}
