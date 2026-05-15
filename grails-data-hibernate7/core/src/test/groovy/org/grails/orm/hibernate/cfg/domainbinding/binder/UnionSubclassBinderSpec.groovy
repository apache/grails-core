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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec

import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UnionSubclass

/**
 * Tests for UnionSubclassBinder using real entity classes.
 */
class UnionSubclassBinderSpec extends HibernateGormDatastoreSpec {

    UnionSubclassBinder binder
    ClassBinder classBinder

    void setup() {
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        classBinder = new ClassBinder(buildingContext.getMetadataCollector())
        binder = new UnionSubclassBinder(buildingContext, namingStrategy, classBinder, buildingContext.getMetadataCollector())
    }

    void 'test bind union subclass with real entities'() {

        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def mappings = buildingContext.getMetadataCollector()
        
        // Register entities in mapping context
        def rootEntity = createPersistentEntity(UnionSubClassRoot) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
        def subEntity = createPersistentEntity(UnionSubClassSub) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
        
        // Setup Hibernate RootClass
        def rootClass = new RootClass(buildingContext)
        rootClass.setEntityName(UnionSubClassRoot.name)
        def rootTable = new Table('US_ROOT_TABLE')
        rootTable.setName('US_ROOT_TABLE')
        rootClass.setTable(rootTable)
        
        // Setup UnionSubclass
        // def unionSubclass = new UnionSubclass(rootClass, buildingContext)
        // unionSubclass.setEntityName(UnionSubClassSub.name)

        when:
        def unionSubclass = binder.bindUnionSubclass(subEntity, rootClass)

        then:
        unionSubclass != null
        unionSubclass.getEntityName() == UnionSubClassSub.name
        unionSubclass.getTable() != null
        unionSubclass.getTable().getName() != 'US_ROOT_TABLE'
        unionSubclass.getClassName() == UnionSubClassSub.name
    }
}

@Entity
class UnionSubClassRoot {

    Long id
}

@Entity
class UnionSubClassSub extends UnionSubClassRoot {

    String name
    static mapping = {
        tablePerHierarchy false
        tablePerConcreteClass true
    }
}
