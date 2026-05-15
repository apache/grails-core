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

package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

import org.hibernate.mapping.ManyToOne
import spock.lang.Subject

class UnidirectionalOneToManyInverseValuesBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    UnidirectionalOneToManyInverseValuesBinder binder

    void setup() {
        binder = new UnidirectionalOneToManyInverseValuesBinder(getGrailsDomainBinder().metadataBuildingContext)
    }

    void 'test bindUnidirectionalOneToManyInverseValues'() {
        given:
        createPersistentEntity(UOTMBook)
        PersistentEntity authorEntity = createPersistentEntity(UOTMAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName('books')

        def owner = new org.hibernate.mapping.RootClass(getGrailsDomainBinder().metadataBuildingContext)
        org.hibernate.mapping.Collection collection = new org.hibernate.mapping.Set(getGrailsDomainBinder().metadataBuildingContext, owner)
        collection.setCollectionTable(new org.hibernate.mapping.Table('UOTM_BOOKS'))

        property.setCollection(collection)

        when:
        ManyToOne manyToOne = binder.bind(property)

        then:
        manyToOne.isIgnoreNotFound() == false
        manyToOne.isLazy() == true
        manyToOne.getReferencedEntityName() == UOTMBook.name
    }

    void 'test bindUnidirectionalOneToManyInverseValues with custom config'() {
        given:
        createPersistentEntity(UOTMBook)
        PersistentEntity authorEntity = createPersistentEntity(UOTMAuthorCustom)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName('books')

        def owner = new org.hibernate.mapping.RootClass(getGrailsDomainBinder().metadataBuildingContext)
        org.hibernate.mapping.Collection collection = new org.hibernate.mapping.Set(getGrailsDomainBinder().metadataBuildingContext, owner)
        collection.setCollectionTable(new org.hibernate.mapping.Table('UOTM_BOOKS_CUSTOM'))

        property.setCollection(collection)

        when:
        ManyToOne manyToOne = binder.bind(property)

        then:
        manyToOne.isIgnoreNotFound() == true
        manyToOne.isLazy() == false
        manyToOne.getReferencedEntityName() == UOTMBook.name
    }
}

@Entity
class UOTMBook {

    Long id
    String title
}

@Entity
class UOTMAuthor {

    Long id
    String name
    Set<UOTMBook> books
    static hasMany = [books: UOTMBook]
}

@Entity
class UOTMAuthorCustom {

    Long id
    String name
    Set<UOTMBook> books
    static hasMany = [books: UOTMBook]
    static mapping = {
        books ignoreNotFound: true, fetch: 'join', lazy: false
    }
}
