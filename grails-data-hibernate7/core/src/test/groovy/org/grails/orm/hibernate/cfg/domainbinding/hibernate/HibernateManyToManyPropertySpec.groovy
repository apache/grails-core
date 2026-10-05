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

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.HibernateMappingContext

class HibernateManyToManyPropertySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HMMPA, HMMPB)
    }

    def "test HibernateManyToManyProperty basic methods"() {
        given:
        def entityA = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HMMPA.name)
        def property = (HibernateManyToManyProperty) entityA.getPropertyByName("others")
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        def rootClass = new org.hibernate.mapping.RootClass(mbc)
        rootClass.setEntityName(HMMPA.name)
        def mockCollection = new org.hibernate.mapping.Set(mbc, rootClass)
        property.setCollection(mockCollection, "")

        expect:
        property.getHibernateAssociatedEntity().name == HMMPB.name
        property.getReferencedEntityName() == HMMPB.name
        property.isManyToMany()
        !property.isOneToMany()
        property.isLazy()
        !property.isAssociationColumnNullable()
    }

    def "test getCollection throws exception if not initialized"() {
        given:
        def entityA = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HMMPA.name)
        def property = (HibernateManyToManyProperty) entityA.getPropertyByName("others")
        property.setHibernateCollection(null)

        when:
        property.getCollection()

        then:
        def e = thrown(org.hibernate.MappingException)
        e.message.contains("Hibernate Collection has not been initialized")
    }

    def "test setCollection with path configures metadata"() {
        given:
        def entityA = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HMMPA.name)
        def property = (HibernateManyToManyProperty) entityA.getPropertyByName("others")
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        def rootClass = new org.hibernate.mapping.RootClass(mbc)
        rootClass.setEntityName(HMMPA.name)
        def mockCollection = new org.hibernate.mapping.Set(mbc, rootClass)

        when:
        property.setCollection(mockCollection, "foo.bar")

        then:
        property.getCollection() == mockCollection
        mockCollection.getRole() == "${HMMPA.name}.foo.bar.others".toString()
        mockCollection.getFetchMode() == property.getFetchMode()
        mockCollection.getBatchSize() == property.getBatchSize()
    }

    def "test validateOwningSide"() {
        given:
        def entityA = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HMMPA.name)
        def propertyA = (HibernateManyToManyProperty) entityA.getPropertyByName("others")
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        
        def rootClass = new org.hibernate.mapping.RootClass(mbc)
        rootClass.setEntityName(HMMPA.name)
        
        def list = new org.hibernate.mapping.List(mbc, rootClass)
        propertyA.setCollection(list, "")

        expect: "Owning side passes"
        propertyA.isOwningSide()
        propertyA.validateOwningSide()

        when: "Non-owning side fails"
        def entityB = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HMMPB.name)
        def propertyB = (HibernateManyToManyProperty) entityB.getPropertyByName("owners")
        propertyB.setCollection(new org.hibernate.mapping.List(mbc, rootClass), "")
        propertyB.validateOwningSide()

        then:
        def e = thrown(org.hibernate.MappingException)
        e.message.contains("List collection types only supported on the owning side")
    }

    def "a many-to-many with no belongsTo on either side has exactly one owning side, chosen by entity name"() {
        given:
        def context = new HibernateMappingContext()
        def left = (HibernatePersistentEntity) context.addPersistentEntity(HMMPNoOwnerLeft)
        def right = (HibernatePersistentEntity) context.addPersistentEntity(HMMPNoOwnerRight)
        def rights = (HibernateManyToManyProperty) left.getPropertyByName("rights")
        def lefts = (HibernateManyToManyProperty) right.getPropertyByName("lefts")

        expect: "the entity whose name sorts first owns the relationship, the other side is the inverse"
        rights.isOwningSide()
        !lefts.isOwningSide()
    }

    def "belongsTo wins over the name order when choosing the owning side"() {
        given:
        def context = new HibernateMappingContext()
        def dependent = (HibernatePersistentEntity) context.addPersistentEntity(HMMPADependent)
        def owner = (HibernatePersistentEntity) context.addPersistentEntity(HMMPZOwner)
        def owners = (HibernateManyToManyProperty) dependent.getPropertyByName("owners")
        def dependents = (HibernateManyToManyProperty) owner.getPropertyByName("dependents")

        expect:
        dependents.isOwningSide()
        !owners.isOwningSide()
    }

    def "a unidirectional many-to-many has no owning side to choose"() {
        given:
        def context = new HibernateMappingContext()
        def entity = (HibernatePersistentEntity) context.addPersistentEntity(HMMPUnidirectional)
        context.addPersistentEntity(HMMPNoOwnerRight)

        expect:
        !entity.getPropertyByName("rights").isOwningSide()
    }
}

@Entity
class HMMPNoOwnerLeft {
    Long id
    Set<HMMPNoOwnerRight> rights
    static hasMany = [rights: HMMPNoOwnerRight]
}

@Entity
class HMMPNoOwnerRight {
    Long id
    Set<HMMPNoOwnerLeft> lefts
    static hasMany = [lefts: HMMPNoOwnerLeft]
}

@Entity
class HMMPUnidirectional {
    Long id
    Set<HMMPNoOwnerRight> rights
    static hasMany = [rights: HMMPNoOwnerRight]
}

@Entity
class HMMPA {
    Long id
    static hasMany = [others: HMMPB]
    static mapping = {
        others joinTable: [name: "h_m_m_p_a_others"]
    }
}

@Entity
class HMMPB {
    Long id
    static hasMany = [owners: HMMPA]
    static belongsTo = [owners: HMMPA]
}

@Entity
class HMMPADependent {
    Long id
    static hasMany = [owners: HMMPZOwner]
    static belongsTo = [owners: HMMPZOwner]
}

@Entity
class HMMPZOwner {
    Long id
    static hasMany = [dependents: HMMPADependent]
}
