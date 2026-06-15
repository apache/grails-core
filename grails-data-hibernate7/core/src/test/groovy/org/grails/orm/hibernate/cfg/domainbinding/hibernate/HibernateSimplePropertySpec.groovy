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
import org.grails.orm.hibernate.cfg.PropertyConfig
import java.lang.annotation.RetentionPolicy

class HibernateSimplePropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HSPEntity])
    }

    def "HibernateSimpleProperty instantiation and basic behavior"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HSPEntity.name)
        def prop = (HibernateSimpleProperty) entity.getPropertyByName("name")

        expect:
        prop.getName() == "name"
        prop.getType() == String
        prop.isLazyAble()
        !prop.isEnumType()
    }

    def "isUserButNotCollectionType logic"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HSPEntity.name)
        def prop = (HibernateSimpleProperty) entity.getPropertyByName("name")
        def config = prop.getMappedForm()

        when:
        config.type = type

        then:
        prop.isUserButNotCollectionType() == result

        where:
        type | result
        null | false
        String | true
        org.hibernate.usertype.UserCollectionType | false
    }

    def "isEnumType coverage"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HSPEntity.name)
        def prop = (HibernateSimpleProperty) entity.getPropertyByName("type")

        expect:
        prop.isEnumType()
        prop.getType() == RetentionPolicy
    }

    def "getTypeName priority logic"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HSPEntity.name)
        def prop = (HibernateSimpleProperty) entity.getPropertyByName("name")
        
        def config1 = new PropertyConfig(type: "custom")
        def mapping1 = Mock(org.grails.orm.hibernate.cfg.Mapping)
        
        def config2 = new PropertyConfig(type: null)
        def mapping2 = Mock(org.grails.orm.hibernate.cfg.Mapping)
        
        def config3 = new PropertyConfig(type: null)
        def mapping3 = Mock(org.grails.orm.hibernate.cfg.Mapping)

        expect: "Config priority"
        prop.getTypeName(String, config1, mapping1) == "custom"

        when: "Mapping priority"
        def res2 = prop.getTypeName(String, config2, mapping2)
        
        then:
        1 * mapping2.getTypeName(String) >> "mapped"
        res2 == "mapped"

        when: "Neither have it"
        def res3 = prop.getTypeName(String, config3, mapping3)
        
        then:
        1 * mapping3.getTypeName(String) >> null
        res3 == String.name
    }
}

@Entity
class HSPEntity {
    Long id
    String name
    RetentionPolicy type
}
