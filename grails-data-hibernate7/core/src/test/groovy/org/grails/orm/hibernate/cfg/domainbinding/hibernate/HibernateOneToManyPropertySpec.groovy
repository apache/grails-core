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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.MappingException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import java.beans.PropertyDescriptor

class HibernateOneToManyPropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HOTMPBook, HOTMPAuthor])
    }

    void "test getReferencedEntityName returns the correct entity name"() {
        given:
        def authorEntity = mappingContext.getPersistentEntity(HOTMPAuthor.name)
        HibernateOneToManyProperty property = (HibernateOneToManyProperty) authorEntity.getPropertyByName("books")

        when:
        String entityName = property.getReferencedEntityName()

        then:
        entityName == HOTMPBook.name
    }

    void "validateProperty throws MappingException for unidirectional one-to-many with sort"() {
        given:
        def entity = Mock(HibernatePersistentEntity) {
            getName() >> "TestEntity"
        }
        def mapping = Mock(PropertyMapping) {
            getMappedForm() >> new PropertyConfig(sort: "name")
        }
        def descriptor = Mock(PropertyDescriptor) {
            getName() >> "items"
        }
        
        def property = new HibernateOneToManyProperty(entity, Mock(MappingContext), descriptor) {
            @Override
            boolean isBidirectional() { false }
            @Override
            PropertyMapping getMapping() { mapping }
        }

        when:
        property.validateProperty()

        then:
        def ex = thrown(MappingException)
        ex.message.contains("are not supported with unidirectional one to many relationships")
    }
}

@Entity
class HOTMPBook {
    Long id
    String title
}

@Entity
class HOTMPAuthor {
    Long id
    String name
    Set<HOTMPBook> books
    static hasMany = [books: HOTMPBook]
}
