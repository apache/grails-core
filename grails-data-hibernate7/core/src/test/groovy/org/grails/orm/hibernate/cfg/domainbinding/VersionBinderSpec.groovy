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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateVersionProperty
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table

class VersionBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    SimpleValueBinder simpleValueBinder
    PropertyBinder propertyBinder
    VersionBinder versionBinder

    def setup() {
        def binder = getGrailsDomainBinder()
        metadataBuildingContext = binder.getMetadataBuildingContext()
        simpleValueBinder = new SimpleValueBinder(metadataBuildingContext, binder.getNamingStrategy(), binder.getJdbcEnvironment())
        propertyBinder = new PropertyBinder()
        
        versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinder, BasicValue::new)
    }

    def "should bind version property correctly"() {
        given:
        def entity = createPersistentEntity(VersionBinderUniqueEntity)
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setTable(new Table("version_binder_unique_entity"))
        entity.setPersistentClass(rootClass)
        def versionProperty = entity.getVersion()
        
        expect:
        versionProperty instanceof HibernateVersionProperty

        when:
        versionBinder.bindVersion(versionProperty, rootClass)
        
        then:
        rootClass.getVersion() != null
        rootClass.getDeclaredVersion() != null
        rootClass.getOptimisticLockStyle() == OptimisticLockStyle.VERSION
        
        def value = rootClass.getVersion().getValue()
        value instanceof BasicValue
        value.getTypeName() == "java.lang.Long"
        
        def column = value.getColumns().first() as Column
        column.getName() == "my_version_col"
    }

    def "should set optimistic lock style to NONE if version is null"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        
        when:
        versionBinder.bindVersion(null, rootClass)
        
        then:
        rootClass.getOptimisticLockStyle() == OptimisticLockStyle.NONE
        rootClass.getVersion() == null
    }

    def "should respect custom column name configured via version DSL"() {
        given:
        def entity = createPersistentEntity(VersionBinderCustomUniqueEntity)
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setTable(new Table("version_binder_custom_unique_entity"))
        entity.setPersistentClass(rootClass)
        def versionProperty = entity.getVersion()
        
        when:
        versionBinder.bindVersion(versionProperty, rootClass)
        
        then:
        rootClass.getVersion() != null
        rootClass.getVersion().getValue().getTypeName() == "java.lang.Long"
        
        def column = rootClass.getVersion().getValue().getColumns().first() as Column
        column.getName() == "my_custom_ver_col"
    }
}

@Entity
class VersionBinderUniqueEntity implements HibernateEntity<VersionBinderUniqueEntity> {
    Long id
    Long version
    static mapping = {
        version column: "my_version_col"
    }
}

@Entity
class VersionBinderCustomUniqueEntity implements HibernateEntity<VersionBinderCustomUniqueEntity> {
    Long id
    Long version
    static mapping = {
        version column: "my_custom_ver_col"
    }
}
