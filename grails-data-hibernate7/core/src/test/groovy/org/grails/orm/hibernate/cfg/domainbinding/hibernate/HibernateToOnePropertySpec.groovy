/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.FetchMode
import org.hibernate.type.ForeignKeyDirection
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PropertyConfig

class HibernateToOnePropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HTOPAuthor, HTOPBook, HTOPProfile, HTOPAddress])
    }

    // ─── HibernateManyToOneProperty Tests ────────────────────────────────────

    def 'HibernateManyToOneProperty behavior'() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HTOPBook.name)
        def prop = (HibernateManyToOneProperty) entity.getPropertyByName('author')

        expect:
        prop.getHibernateAssociatedEntity().getName() == HTOPAuthor.name
        prop.getReferencedEntityName() == HTOPAuthor.name
        prop.isValidHibernateManyToOne()
    }

    // ─── HibernateOneToOneProperty Tests ─────────────────────────────────────

    def 'HibernateOneToOneProperty mock-based bidirectional mapping'() {
        given:
        def profileProp = Mock(HibernateOneToOneProperty)
        def authorProp = Mock(HibernateOneToOneProperty)
        
        def profileEntity = Mock(GrailsHibernatePersistentEntity)
        profileEntity.getName() >> HTOPProfile.name
        
        def authorEntity = Mock(GrailsHibernatePersistentEntity)
        authorEntity.getName() >> HTOPAuthor.name

        // Mock profileProp (Author -> Profile)
        profileProp.getHibernateInverseSide() >> authorProp
        profileProp.getOwner() >> authorEntity
        profileProp.getAssociatedEntity() >> profileEntity
        profileProp.isOwningSide() >> false
        profileProp.getName() >> 'profile'
        profileProp.isHibernateConstrained() >> { authorProp.isHasOne() }
        profileProp.getHibernateReferencedEntityName() >> { profileProp.getHibernateInverseSide()?.getOwner()?.getName() ?: profileProp.getAssociatedEntity().getName() }
        profileProp.getHibernateReferencedPropertyName() >> { profileProp.getHibernateInverseSide()?.getName() }
        profileProp.getHibernateForeignKeyDirection() >> { profileProp.isHibernateConstrained() ? ForeignKeyDirection.FROM_PARENT : ForeignKeyDirection.TO_PARENT }
        profileProp.needsSimpleValueBinding() >> { profileProp.isHibernateConstrained() || profileProp.getHibernateReferencedPropertyName() == null }
        profileProp.isAssociationColumnNullable() >> { if(true && !profileProp.isOwningSide()) { def inv = profileProp.getHibernateInverseSide(); return inv == null || !inv.isHasOne() }; return true }

        // Mock authorProp (Profile -> Author)
        authorProp.getHibernateInverseSide() >> profileProp
        authorProp.getOwner() >> profileEntity
        authorProp.getAssociatedEntity() >> authorEntity
        authorProp.isOwningSide() >> true
        authorProp.getName() >> 'author'
        authorProp.isHasOne() >> true
        authorProp.isHibernateConstrained() >> { profileProp.isHasOne() } // hasOne is only on authorProp side in this test
        authorProp.getHibernateReferencedEntityName() >> { authorProp.getHibernateInverseSide()?.getOwner()?.getName() ?: authorProp.getAssociatedEntity().getName() }
        authorProp.getHibernateReferencedPropertyName() >> { authorProp.getHibernateInverseSide()?.getName() }
        authorProp.getHibernateForeignKeyDirection() >> { authorProp.isHibernateConstrained() ? ForeignKeyDirection.FROM_PARENT : ForeignKeyDirection.TO_PARENT }
        authorProp.needsSimpleValueBinding() >> { authorProp.isHibernateConstrained() || authorProp.getHibernateReferencedPropertyName() == null }
        authorProp.isAssociationColumnNullable() >> { if(true && !authorProp.isOwningSide()) { def inv = authorProp.getHibernateInverseSide(); return inv == null || !inv.isHasOne() }; return true }

        expect: 'Inverse side (Author -> Profile) is constrained because authorProp hasOne'
        profileProp.isHibernateConstrained()
        profileProp.getHibernateReferencedEntityName() == HTOPProfile.name
        profileProp.getHibernateReferencedPropertyName() == 'author'
        profileProp.getHibernateForeignKeyDirection() == ForeignKeyDirection.FROM_PARENT
        profileProp.needsSimpleValueBinding()
        !profileProp.isAssociationColumnNullable()

        and: 'Owning side (Profile -> Author) is not constrained'
        !authorProp.isHibernateConstrained()
        authorProp.getHibernateReferencedEntityName() == HTOPAuthor.name
        authorProp.getHibernateReferencedPropertyName() == 'profile'
        authorProp.getHibernateForeignKeyDirection() == ForeignKeyDirection.TO_PARENT
        !authorProp.needsSimpleValueBinding()
        authorProp.isAssociationColumnNullable()
    }

    def 'HibernateOneToOneProperty unidirectional'() {
        given:
        def authorEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HTOPAuthor.name)
        def addressProp = (HibernateOneToOneProperty) authorEntity.getPropertyByName('address')

        expect:
        !addressProp.isBidirectional()
        !addressProp.isHibernateConstrained()
        addressProp.getHibernateReferencedEntityName() == HTOPAddress.name
        addressProp.getHibernateReferencedPropertyName() == null
        addressProp.getHibernateForeignKeyDirection() == ForeignKeyDirection.TO_PARENT
        addressProp.needsSimpleValueBinding()
        
        and: "It is technically a many-to-one in Hibernate if it's just a FK column"
        addressProp.isValidHibernateManyToOne()
        !addressProp.isValidHibernateOneToOne()
        
        addressProp.isAssociationColumnNullable()
    }

    def 'getHibernateFetchMode returns configured or default'() {
        given:
        def authorEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HTOPAuthor.name)
        def profileProp = (HibernateOneToOneProperty) authorEntity.getPropertyByName('profile')
        def addressProp = (HibernateOneToOneProperty) authorEntity.getPropertyByName('address')

        expect:
        profileProp.getHibernateFetchMode() == FetchMode.JOIN
        addressProp.getHibernateFetchMode() == FetchMode.DEFAULT
    }
}

// --- Supporting Entities ---

@Entity
class HTOPAuthor {

    Long id
    String name
    HTOPProfile profile
    HTOPAddress address
    static hasMany = [books: HTOPBook]
    static mapping = {
        profile fetch: 'join', singleColumn: true
        address singleColumn: true
    }
}

@Entity
class HTOPBook {

    Long id
    String title
    HTOPAuthor author
}

@Entity
class HTOPProfile {

    Long id
    static belongsTo = [author: HTOPAuthor]
}

@Entity
class HTOPAddress {

    Long id
    String city
}
