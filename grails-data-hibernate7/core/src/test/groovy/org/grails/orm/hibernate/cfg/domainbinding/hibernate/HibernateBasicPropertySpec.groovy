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
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.PropertyConfig
import java.lang.annotation.RetentionPolicy

class HibernateBasicPropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HBPPerson])
    }

    def "test getCollection throws exception if not initialized"() {
        given:
        def personEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HBPPerson.name)
        def property = (HibernateBasicProperty) personEntity.getPropertyByName("tags")
        def original = property.getHibernateCollection()
        property.setHibernateCollection(null)

        when:
        property.getCollection()

        then:
        def e = thrown(org.hibernate.MappingException)
        e.message.contains("Hibernate Collection has not been initialized")
        
        cleanup:
        property.setHibernateCollection(original)
    }

    def "test setCollection with path configures metadata"() {
        given:
        def personEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HBPPerson.name)
        def property = (HibernateBasicProperty) personEntity.getPropertyByName("tags")
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        
        def rootClass = new org.hibernate.mapping.RootClass(mbc)
        rootClass.setEntityName(HBPPerson.name)
        def mockCollection = new org.hibernate.mapping.Set(mbc, rootClass)
        
        when:
        property.setCollection(mockCollection, "foo.bar")

        then:
        property.getCollection() == mockCollection
        mockCollection.getRole() == "${HBPPerson.name}.foo.bar.tags".toString()
        mockCollection.getFetchMode() == property.getFetchMode()
        mockCollection.getBatchSize() == property.getBatchSize()
    }

    def "getElementTypeName returns the Hibernate type name for the element type"() {
        given:
        def personEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HBPPerson.name)
        def property = (HibernateBasicProperty) personEntity.getPropertyByName("tags")

        expect:
        property.getElementTypeName() == 'java.lang.String'
    }

    // ─── Tests using a stub to reach deep logic ──────────────────────────────

    def "isUserButNotCollectionType logic"() {
        given:
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(HBPPerson.name)
        def config = new PropertyConfig()
        def assoc = new TestHibernateBasicProperty(entity, String, config)

        when:
        config.type = type

        then:
        assoc.isUserButNotCollectionType() == result

        where:
        type | result
        null | false
        String | true
        org.hibernate.usertype.UserCollectionType | false
    }

    def "isEnumType coverage"() {
        given:
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(HBPPerson.name)
        def assoc = new TestHibernateBasicProperty(entity, type, null)

        expect:
        assoc.isEnumType() == result

        where:
        type | result
        String | false
        RetentionPolicy | true
    }

    def "getTypeName priority logic"() {
        given:
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(HBPPerson.name)
        def assoc = new TestHibernateBasicProperty(entity, String, null)
        
        def config1 = new PropertyConfig(type: "custom")
        def mapping1 = Mock(org.grails.orm.hibernate.cfg.Mapping)
        
        def config2 = new PropertyConfig(type: null)
        def mapping2 = Mock(org.grails.orm.hibernate.cfg.Mapping)
        
        def config3 = new PropertyConfig(type: null)
        def mapping3 = Mock(org.grails.orm.hibernate.cfg.Mapping)
        
        def config4 = new PropertyConfig(type: null)

        expect: "Config priority"
        assoc.getTypeName(String, config1, mapping1) == "custom"

        when: "Mapping priority"
        def res2 = assoc.getTypeName(String, config2, mapping2)
        
        then:
        1 * mapping2.getTypeName(String) >> "mapped"
        res2 == "mapped"

        when: "Neither have it"
        def res3 = assoc.getTypeName(String, config3, mapping3)
        
        then:
        1 * mapping3.getTypeName(String) >> null
        res3 == String.name

        when: "Mapping is null"
        def res4 = assoc.getTypeName(String, config4, null)
        
        then:
        res4 == String.name
    }

    static class TestHibernateBasicProperty extends HibernateBasicProperty {
        Class typeField
        PropertyConfig mappedFormField

        TestHibernateBasicProperty(GrailsHibernatePersistentEntity entity, Class type, PropertyConfig mappedForm) {
            super(entity, entity.getMappingContext(), new java.beans.PropertyDescriptor("name", HBPPerson, "getName", null))
            this.typeField = type
            this.mappedFormField = mappedForm
        }

        @Override Class getType() { typeField ?: super.getType() }
        @Override PropertyConfig getMappedForm() { mappedFormField ?: super.getMappedForm() }

        @Override PropertyMapping<PropertyConfig> getMapping() { 
            return new PropertyMapping<PropertyConfig>() {
                @Override ClassMapping getClassMapping() { null }
                @Override PropertyConfig getMappedForm() { mappedFormField }
            }
        }
    }
}

@Entity
class HBPPerson {
    Long id
    String name
    Set<String> tags
    static hasMany = [tags: String]
}
