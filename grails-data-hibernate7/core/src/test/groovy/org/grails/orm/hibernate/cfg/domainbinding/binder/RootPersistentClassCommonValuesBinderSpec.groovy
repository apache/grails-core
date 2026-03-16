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
import org.hibernate.boot.spi.MetadataBuildingContext
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator

import org.hibernate.mapping.BasicValue

class RootPersistentClassCommonValuesBinderSpec extends HibernateGormDatastoreSpec {

    RootPersistentClassCommonValuesBinder binder
    MetadataBuildingContext metadataBuildingContext
    PersistentEntityNamingStrategy namingStrategy
    IdentityBinder identityBinder
    VersionBinder versionBinder
    ClassBinder classBinder
    ClassPropertiesBinder classPropertiesBinder
    GrailsDomainBinder gormDomainBinder

    void setup() {
        manager.addAllDomainClasses([TestEntity, AbstractTestEntity])
        
        gormDomainBinder = getGrailsDomainBinder()
        metadataBuildingContext = gormDomainBinder.getMetadataBuildingContext()
        namingStrategy = gormDomainBinder.getNamingStrategy()
        def jdbcEnvironment = gormDomainBinder.getJdbcEnvironment()
        def simpleValueBinder = new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        def propertyBinder = new PropertyBinder()
        def simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, new BasicValueIdCreator(jdbcEnvironment, namingStrategy), simpleValueBinder, propertyBinder)
        def compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, null, null)
        identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinder, BasicValue::new)
        classBinder = new ClassBinder()
        classPropertiesBinder = Mock(ClassPropertiesBinder)

        binder = new RootPersistentClassCommonValuesBinder(
                metadataBuildingContext,
                namingStrategy,
                identityBinder,
                versionBinder,
                classBinder,
                classPropertiesBinder
        )
    }

    void "test bindRootPersistentClassCommonValues binds properties correctly"() {
        given:
        def entity = createPersistentEntity(TestEntity)
        def mappings = getCollector()

        when:
        RootClass rootClass = binder.bindRootPersistentClassCommonValues(entity, [], mappings)

        then:
        1 * classPropertiesBinder.bindClassProperties(entity, _, mappings)
        rootClass != null
        rootClass.getEntityName() == TestEntity.name
        rootClass.isAbstract() == false
        rootClass.getTable().getName() == namingStrategy.resolveTableName("TestEntity")
    }

    void "test bindRootPersistentClassCommonValues for abstract entity"() {
        given:
        def entity = createPersistentEntity(AbstractTestEntity)
        def mappings = getCollector()

        when:
        RootClass rootClass = binder.bindRootPersistentClassCommonValues(entity, [], mappings)

        then:
        rootClass != null
        rootClass.isAbstract() == true
    }
}

@Entity
class TestEntity {
    Long id
    Long version
    String name
}

@Entity
abstract class AbstractTestEntity {
    Long id
    Long version
    String name
}
