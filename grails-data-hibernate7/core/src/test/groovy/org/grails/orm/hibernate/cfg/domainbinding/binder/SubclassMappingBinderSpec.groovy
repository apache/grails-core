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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec

import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.Subclass
import org.hibernate.mapping.UnionSubclass

class SubclassMappingBinderSpec extends HibernateGormDatastoreSpec {

    SubclassMappingBinder binder
    MetadataBuildingContext metadataBuildingContext
    JoinedSubClassBinder joinedSubClassBinder
    UnionSubclassBinder unionSubclassBinder
    SingleTableSubclassBinder singleTableSubclassBinder
    ClassPropertiesBinder classPropertiesBinder

    void setup() {
        def gdb = getGrailsDomainBinder()
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        joinedSubClassBinder = Mock(JoinedSubClassBinder)
        unionSubclassBinder = Mock(UnionSubclassBinder)
        singleTableSubclassBinder = Mock(SingleTableSubclassBinder)
        classPropertiesBinder = Mock(ClassPropertiesBinder)
        
        binder = new SubclassMappingBinder(
                joinedSubClassBinder,
                unionSubclassBinder,
                singleTableSubclassBinder,
                classPropertiesBinder
        )
    }

    def "test createSubclassMapping for single table inheritance"() {
        given:
        createPersistentEntity(SMBSSingleSuper)
        // Cast the created persistent entity to HibernatePersistentEntity
        def subEntity = createPersistentEntity(SMBSSingleSub) as HibernatePersistentEntity
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(SMBSSingleSuper.name)
        rootClass.setJpaEntityName(SMBSSingleSuper.name)
        def mappings = getCollector()

        when:
        Subclass subClass = binder.createSubclassMapping(subEntity, rootClass)

        then:
        subEntity != null
        1 * singleTableSubclassBinder.bindSubClass(subEntity, _ as SingleTableSubclass)
        1 * classPropertiesBinder.bindClassProperties(subEntity, _ as Subclass)
        subClass instanceof SingleTableSubclass
        subClass.getEntityName() == SMBSSingleSub.name
    }

    def "test createSubclassMapping for joined table inheritance"() {
        given:
        createPersistentEntity(SMBSJoinedSuper)
        // Cast the created persistent entity to HibernatePersistentEntity
        def subEntity = createPersistentEntity(SMBSJoinedSub) as HibernatePersistentEntity
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(SMBSJoinedSuper.name)
        rootClass.setJpaEntityName(SMBSJoinedSuper.name)
        def mappings = getCollector()

        when:
        Subclass subClass = binder.createSubclassMapping(subEntity, rootClass)

        then:
        subEntity != null
        1 * joinedSubClassBinder.bindJoinedSubClass(subEntity, _ as JoinedSubclass)
        1 * classPropertiesBinder.bindClassProperties(subEntity, _ as Subclass)
        subClass instanceof JoinedSubclass
        subClass.getEntityName() == SMBSJoinedSub.name
    }

    def "test createSubclassMapping for table per concrete class inheritance"() {
        given:
        createPersistentEntity(SMBSUnionSuper)
        // Cast the created persistent entity to HibernatePersistentEntity
        def subEntity = createPersistentEntity(SMBSUnionSub) as HibernatePersistentEntity
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(SMBSUnionSuper.name)
        rootClass.setJpaEntityName(SMBSUnionSuper.name)
        def mappings = getCollector()

        when:
        Subclass subClass = binder.createSubclassMapping(subEntity, rootClass)

        then:
        subEntity != null
        1 * unionSubclassBinder.bindUnionSubclass(subEntity, _ as UnionSubclass)
        1 * classPropertiesBinder.bindClassProperties(subEntity, _ as Subclass)
        subClass instanceof UnionSubclass
        subClass.getEntityName() == SMBSUnionSub.name
    }
}

@Entity
class SMBSSingleSuper {
    Long id
    String name
}

@Entity
class SMBSSingleSub extends SMBSSingleSuper {
    String subName
}

@Entity
class SMBSJoinedSuper {
    Long id
    String name
    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class SMBSJoinedSub extends SMBSJoinedSuper {
    String subName
}

@Entity
class SMBSUnionSuper {
    Long id
    String name
    static mapping = {
        tablePerHierarchy false
        tablePerConcreteClass true
    }
}

@Entity
class SMBSUnionSub extends SMBSUnionSuper {
    String subName
}
