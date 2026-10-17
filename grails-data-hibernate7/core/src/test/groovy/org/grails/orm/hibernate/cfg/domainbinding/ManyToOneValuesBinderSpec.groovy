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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.Hibernate
import org.hibernate.engine.FetchStyle
import org.hibernate.mapping.ManyToOne
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder

class ManyToOneValuesBinderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ManyToOneValuesBinderBook, ManyToOneValuesBinderAuthor, ManyToOneValuesBinderUnproxyableAuthor)
    }

    @Unroll
    def "Test bindManyToOneValues with #scenario"() {
        given:
        // 1. Mock the dependency and use the protected constructor
        def binder = new ManyToOneValuesBinder()

        // 2. Set up mocks for the method arguments
        def association = Mock(HibernateAssociation)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(),null)
        def associatedEntity = Mock(PersistentEntity)

        // 3. Create the config object that the converter will return
        def config = new PropertyConfig()
        if (testFetchMode != null) {
            config.setFetch(testFetchMode)
        }
        config.setLazy(testLazy)
        config.setIgnoreNotFound(testIgnoreNotFound)

        // 4. Define mock behaviors
        association.getMappedForm() >> config
        association.getHibernateMappedForm() >> config
        association.getAssociatedEntity() >> associatedEntity
        association.isLazy() >> expectedLazy
        associatedEntity.getName() >> "AssociatedEntityName"

        when:
        binder.bindManyToOneValues(association, manyToOne)

        then:
        // 5. Verify that the correct values were set on the ManyToOne object
        manyToOne.getFetchMode() == expectedFetchMode
        manyToOne.isLazy() == expectedLazy
        manyToOne.isIgnoreNotFound() == testIgnoreNotFound
        manyToOne.getReferencedEntityName() == "AssociatedEntityName"

        where:
        scenario                | testFetchMode    | testLazy | testIgnoreNotFound | expectedFetchMode | expectedLazy
        "explicit values"       | FetchMode.JOIN   | true     | true               | FetchMode.JOIN    | true
        "default lazy values"   | null             | null     | false              | FetchMode.SELECT  | true
        "default eager values"  | null             | false    | false              | FetchMode.DEFAULT | false
        "other explicit values" | FetchMode.SELECT | false    | false              | FetchMode.SELECT  | false
    }

    @Unroll
    void "the many-to-one #property is bound with fetch mode #fetchMode and fetched with #style"() {
        when:
        def value = datastore.metadata.getEntityBinding(ManyToOneValuesBinderBook.name).getProperty(property).value as ManyToOne
        def fetchOptions = sessionFactory.mappingMetamodel
                .getEntityDescriptor(ManyToOneValuesBinderBook.name)
                .findAttributeMapping(property)
                .mappedFetchOptions

        then:
        value.fetchMode == fetchMode
        value.lazy == lazy
        fetchOptions.style == style

        where:
        property    | fetchMode         | lazy  | style
        'author'    | FetchMode.SELECT  | true  | FetchStyle.SELECT
        'reviewer'  | FetchMode.SELECT  | true  | FetchStyle.SELECT
        'publisher' | FetchMode.DEFAULT | false | FetchStyle.SELECT
        'editor'    | FetchMode.JOIN    | false | FetchStyle.JOIN
    }

    void "loading a book leaves a lazy author unloaded and loads the associations that cannot be deferred"() {
        given:
        Long bookId = ManyToOneValuesBinderBook.withTransaction {
            new ManyToOneValuesBinderBook(
                    title: 'Title',
                    author: new ManyToOneValuesBinderAuthor(name: 'Author').save(),
                    reviewer: new ManyToOneValuesBinderUnproxyableAuthor(name: 'Reviewer').save(),
                    publisher: new ManyToOneValuesBinderAuthor(name: 'Publisher').save(),
                    editor: new ManyToOneValuesBinderAuthor(name: 'Editor').save()
            ).save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        Map<String, Boolean> initialized = ManyToOneValuesBinderBook.withTransaction {
            ManyToOneValuesBinderBook book = ManyToOneValuesBinderBook.get(bookId)
            ['author', 'reviewer', 'publisher', 'editor'].collectEntries { [it, Hibernate.isInitialized(book[it])] }
        }

        then:
        !initialized.author
        initialized.reviewer
        initialized.publisher
        initialized.editor
    }
}

@Entity
class ManyToOneValuesBinderBook implements HibernateEntity<ManyToOneValuesBinderBook> {
    String title
    ManyToOneValuesBinderAuthor author
    ManyToOneValuesBinderUnproxyableAuthor reviewer
    ManyToOneValuesBinderAuthor publisher
    ManyToOneValuesBinderAuthor editor

    static mapping = {
        publisher lazy: false
        editor fetch: 'join'
    }
}

@Entity
class ManyToOneValuesBinderAuthor implements HibernateEntity<ManyToOneValuesBinderAuthor> {
    String name
}

/**
 * A final getter stops Hibernate from creating a proxy for this entity. That is the case in which
 * Hibernate resolves the DEFAULT fetch mode of a many-to-one to a JOIN.
 */
@Entity
class ManyToOneValuesBinderUnproxyableAuthor implements HibernateEntity<ManyToOneValuesBinderUnproxyableAuthor> {
    String name

    final String getName() {
        name
    }
}
