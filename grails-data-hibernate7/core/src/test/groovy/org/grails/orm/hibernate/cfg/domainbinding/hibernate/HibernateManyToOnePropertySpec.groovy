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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty

class HibernateManyToOnePropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HMTOPBook, HMTOPAuthor])
    }

    void "test getReferencedEntityName returns the correct entity name"() {
        given:
        def bookEntity = mappingContext.getPersistentEntity(HMTOPBook.name)
        HibernateManyToOneProperty property = (HibernateManyToOneProperty) bookEntity.getPropertyByName("author")

        when:
        String entityName = property.getReferencedEntityName()

        then:
        entityName == HMTOPAuthor.name
    }
}

@Entity
class HMTOPBook {
    Long id
    String title
    HMTOPAuthor author
}

@Entity
class HMTOPAuthor {
    Long id
    String name
    Set<HMTOPBook> books
    static hasMany = [books: HMTOPBook]
}
