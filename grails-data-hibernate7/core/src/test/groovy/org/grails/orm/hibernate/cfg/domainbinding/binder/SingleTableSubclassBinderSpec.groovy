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
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.SingleTableSubclass

/**
 * Tests for SingleTableSubclassBinder using real entity classes.
 */
class SingleTableSubclassBinderSpec extends HibernateGormDatastoreSpec {

    SingleTableSubclassBinder binder
    ClassBinder classBinder = new ClassBinder()

    void setup() {
        binder = new SingleTableSubclassBinder(classBinder)
    }

    void "test bind single table subclass with real entities"() {
        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def mappings = buildingContext.getMetadataCollector()
        
        // Register entities in mapping context
        def rootEntity = createPersistentEntity(SingleTableSubClassRoot)
        def subEntity = createPersistentEntity(SingleTableSubClassSub)
        
        // Setup Hibernate RootClass
        def rootClass = new RootClass(buildingContext)
        rootClass.setEntityName(SingleTableSubClassRoot.name)
        def rootTable = new Table("ST_ROOT_TABLE")
        rootTable.setName("ST_ROOT_TABLE")
        rootClass.setTable(rootTable)
        
        // Setup SingleTableSubclass
        def singleTableSubclass = new SingleTableSubclass(rootClass, buildingContext)
        singleTableSubclass.setEntityName(SingleTableSubClassSub.name)

        when:
        binder.bindSubClass(subEntity, singleTableSubclass, mappings)

        then:
        singleTableSubclass.getTable() == rootTable
        singleTableSubclass.getDiscriminatorValue() == "SUB_CLASS"
    }
}

@Entity
class SingleTableSubClassRoot {
    Long id
}

@Entity
class SingleTableSubClassSub extends SingleTableSubClassRoot {
    String name
    static mapping = {
        discriminator "SUB_CLASS"
    }
}
