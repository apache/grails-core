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
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher

import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table

/**
 * Tests for JoinedSubClassBinder using real entity classes.
 */
class JoinedSubClassBinderSpec extends HibernateGormDatastoreSpec {

    JoinedSubClassBinder binder
    ColumnNameForPropertyAndPathFetcher fetcher
    ClassBinder classBinder
    SimpleValueColumnBinder simpleValueColumnBinder = new SimpleValueColumnBinder()

    void setup() {
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        classBinder = new ClassBinder(buildingContext.getMetadataCollector())
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover()
        def defaultColumnNameFetcher = new org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        
        fetcher = new org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        binder = new JoinedSubClassBinder(buildingContext, namingStrategy, simpleValueColumnBinder, fetcher, classBinder, buildingContext.getMetadataCollector())
    }

    void "test bind joined subclass with real entities"() {
        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def mappings = buildingContext.getMetadataCollector()
        
        // Register entities in mapping context
        def rootEntity = createPersistentEntity(JoinedSubClassRoot) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
        def subEntity = createPersistentEntity(JoinedSubClassSub) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
        
        // Setup Hibernate RootClass
        def rootClass = new RootClass(buildingContext)
        rootClass.setEntityName(JoinedSubClassRoot.name)
        def rootTable = new Table("JS_ROOT_TABLE")
        rootTable.setName("JS_ROOT_TABLE")
        rootClass.setTable(rootTable)
        
        def idProperty = new org.hibernate.mapping.Property()
        idProperty.setName("id")
        def idValue = new org.hibernate.mapping.BasicValue(buildingContext, rootTable)
        idValue.setTypeName("long")
        idProperty.setValue(idValue)
        rootClass.setIdentifier(idValue)
        rootClass.setIdentifierProperty(idProperty)
        rootClass.createPrimaryKey()
        
        // The JoinedSubclass needs the parent PersistentClass
        // def joinedSubclass = new JoinedSubclass(rootClass, buildingContext)
        // joinedSubclass.setEntityName(JoinedSubClassSub.name)

        when:
        def joinedSubclass = binder.bindJoinedSubClass(subEntity, rootClass)

        then:
        joinedSubclass != null
        joinedSubclass.getEntityName() == JoinedSubClassSub.name
        joinedSubclass.getTable() != null
        joinedSubclass.getTable().getName() != "JS_ROOT_TABLE"
        joinedSubclass.getKey() != null
        joinedSubclass.getKey().getColumnSpan() > 0
        joinedSubclass.getTable().getPrimaryKey() != null
    }
}

@Entity
class JoinedSubClassRoot {
    Long id
}

@Entity
class JoinedSubClassSub extends JoinedSubClassRoot {
    String name
    static mapping = {
        tablePerHierarchy false
    }
}
