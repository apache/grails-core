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

package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set as HibernateSet
import org.hibernate.mapping.Table
import spock.lang.Subject

class SetCollectionTypeSpec extends HibernateGormDatastoreSpec {

    def "should create a Set and delegate to binder"() {
        given:
        def binder = Mock(GrailsDomainBinder)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder.getMetadataBuildingContext() >> metadataBuildingContext
        
        @Subject
        def collectionType = new SetCollectionType(metadataBuildingContext)
        
        def property = Mock(HibernateToManyProperty)
        def owner = new RootClass(metadataBuildingContext)
        def table = new Table("test_table")
        owner.setTable(table)
        
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        property.getOwner() >> domainClass
        domainClass.getMappedForm() >> null
        
        def mappings = Mock(InFlightMetadataCollector)
        def path = "testPath"
        def sessionFactoryBeanName = "sessionFactory"

        when:
        def result = collectionType.create(property, owner)

        then:
        result instanceof HibernateSet
        result.getCollectionTable() == table
    }
}