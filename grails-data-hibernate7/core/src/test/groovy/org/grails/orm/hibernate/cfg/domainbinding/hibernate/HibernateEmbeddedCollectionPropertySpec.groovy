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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.cfg.PropertyConfig
import java.beans.PropertyDescriptor

class HibernateEmbeddedCollectionPropertySpec extends HibernateGormDatastoreSpec {

    def "test getCollection throws exception if not initialized"() {
        given:
        def entity = Mock(HibernatePersistentEntity) {
            getName() >> "TestEntity"
        }
        def descriptor = Mock(PropertyDescriptor) {
            getName() >> "items"
        }
        def property = new HibernateEmbeddedCollectionProperty(entity, Mock(MappingContext), descriptor)

        when:
        property.getCollection()

        then:
        def e = thrown(org.hibernate.MappingException)
        e.message.contains("Hibernate Collection has not been initialized")
    }

    def "test setCollection with path configures metadata"() {
        given:
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        def entity = Mock(HibernatePersistentEntity) {
            getName() >> "TestEntity"
        }
        def descriptor = Mock(PropertyDescriptor) {
            getName() >> "items"
        }
        def propertyConfig = new PropertyConfig(fetch: "select", batchSize: 10, cascade: "all")
        
        def property = new HibernateEmbeddedCollectionProperty(entity, Mock(MappingContext), descriptor) {
            @Override
            PropertyConfig getHibernateMappedForm() { propertyConfig }
        }
        
        def rootClass = new org.hibernate.mapping.RootClass(mbc)
        rootClass.setEntityName("TestEntity")
        def mockCollection = new org.hibernate.mapping.Set(mbc, rootClass)
        
        when:
        property.setCollection(mockCollection, "foo.bar")

        then:
        property.getCollection() == mockCollection
        mockCollection.getRole() == "TestEntity.foo.bar.items".toString()
        mockCollection.getFetchMode() == org.hibernate.FetchMode.SELECT
        mockCollection.getBatchSize() == 10
    }
}
