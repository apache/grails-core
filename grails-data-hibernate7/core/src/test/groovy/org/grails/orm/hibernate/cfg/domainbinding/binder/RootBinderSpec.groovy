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


import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.RootClass

class RootBinderSpec extends HibernateGormDatastoreSpec {

    RootBinder binder
    MultiTenantFilterBinder multiTenantFilterBinder
    SubClassBinder subClassBinder
    RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder
    DiscriminatorPropertyBinder discriminatorPropertyBinder
    MetadataBuildingContext metadataBuildingContext
    PersistentEntityNamingStrategy namingStrategy

    void setup() {
        def gdb = getGrailsDomainBinder()
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        namingStrategy = gdb.getNamingStrategy()
        
        multiTenantFilterBinder = Mock(MultiTenantFilterBinder)
        subClassBinder = Mock(SubClassBinder)
        rootPersistentClassCommonValuesBinder = Mock(RootPersistentClassCommonValuesBinder)
        discriminatorPropertyBinder = Mock(DiscriminatorPropertyBinder)
        
        binder = new RootBinder(
                "default",
                multiTenantFilterBinder,
                subClassBinder,
                rootPersistentClassCommonValuesBinder,
                discriminatorPropertyBinder
        )
    }

    def "test bindRoot with no children"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> "Parent"
        entity.getChildEntities("default") >> []
        entity.getMappedForm() >> new Mapping()
        
        def mappings = getCollector()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")

        when:
        binder.bindRoot(entity, mappings)

        then:
        1 * rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(entity, [], mappings) >> rootClass
        0 * discriminatorPropertyBinder.bindDiscriminatorProperty(_)
        0 * subClassBinder.bindSubClass(_, _, _)
        1 * multiTenantFilterBinder.bind(entity, rootClass)
        mappings.getEntityBinding("Parent") == rootClass
    }

    def "test bindRoot with children and table-per-hierarchy"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def childEntity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> "Parent"
        entity.getChildEntities("default") >> [childEntity]
        def mapping = new Mapping()
        mapping.setTablePerHierarchy(true)
        entity.getMappedForm() >> mapping
        entity.isTablePerHierarchy() >> true
        
        def mappings = getCollector()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")

        when:
        binder.bindRoot(entity, mappings)

        then:
        1 * rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(entity, [childEntity], mappings) >> rootClass
        1 * discriminatorPropertyBinder.bindDiscriminatorProperty(rootClass)
        1 * subClassBinder.bindSubClass(childEntity, rootClass, mappings)
        1 * multiTenantFilterBinder.bind(entity, rootClass)
        mappings.getEntityBinding("Parent") == rootClass
    }

    def "test bindRoot already mapped"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> "Parent"
        def mappings = getCollector()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")
        mappings.addEntityBinding(rootClass)

        when:
        binder.bindRoot(entity, mappings)

        then:
        0 * rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(_, _, _)
    }
}
