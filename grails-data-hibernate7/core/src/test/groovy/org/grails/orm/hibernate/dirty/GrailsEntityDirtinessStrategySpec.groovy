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
package org.grails.orm.hibernate.dirty

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.hibernate.CustomEntityDirtinessStrategy
import org.hibernate.Session
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.entity.EntityPersister

class GrailsEntityDirtinessStrategySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([DSBook, DSEmbeddedEntity])
    }

    def "canDirtyCheck returns true for DirtyCheckable"() {
        given:
        def strategy = new GrailsEntityDirtinessStrategy()
        def book = new DSBook()
        def nonDirty = "string"

        expect:
        strategy.canDirtyCheck(book, null, null)
        !strategy.canDirtyCheck(nonDirty, null, null)
    }

    def "isDirty returns true if not in session or has changes"() {
        given:
        def strategy = new GrailsEntityDirtinessStrategy()
        def nativeSession = sessionFactory.getCurrentSession()
        def book = new DSBook(title: "T1").save(flush: true)
        
        expect:
        !strategy.isDirty(book, null, nativeSession)
        
        when:
        book.title = "T2"
        
        then:
        strategy.isDirty(book, null, nativeSession)
        
        when:
        nativeSession.evict(book)
        
        then:
        strategy.isDirty(book, null, nativeSession)
    }

    def "findDirty handles various attribute types and statuses"() {
        given:
        def strategy = new GrailsEntityDirtinessStrategy()
        def nativeSession = sessionFactory.getCurrentSession()
        def sessionImplementor = nativeSession as SessionImplementor
        def persister = sessionImplementor.getSessionFactory().getRuntimeMetamodels().getMappingMetamodel().getEntityDescriptor(DSBook) as EntityPersister
        def context = Mock(CustomEntityDirtinessStrategy.DirtyCheckContext)
        
        def book = new DSBook(title: "B1", lastUpdated: new Date())
        // book is NOT in session yet, so status will be null
        
        when:
        strategy.findDirty(book, persister, nativeSession, context)
        
        then:
        1 * context.doDirtyChecking(_) >> { args ->
            def checker = args[0] as CustomEntityDirtinessStrategy.AttributeChecker
            def info = Mock(CustomEntityDirtinessStrategy.AttributeInformation)
            assert checker.isDirty(info) == true // null status means always dirty
        }

        when:
        book.save(flush: true)
        book.title = "B2" // make it dirty
        strategy.findDirty(book, persister, nativeSession, context)

        then:
        1 * context.doDirtyChecking(_) >> { args ->
            def checker = args[0] as CustomEntityDirtinessStrategy.AttributeChecker
            
            def infoTitle = Mock(CustomEntityDirtinessStrategy.AttributeInformation)
            infoTitle.getName() >> "title"
            assert checker.isDirty(infoTitle) == true
            
            def infoOther = Mock(CustomEntityDirtinessStrategy.AttributeInformation)
            infoOther.getName() >> "other"
            assert checker.isDirty(infoOther) == false
        }
    }

    def "findDirty handles lastUpdated property"() {
        given:
        def strategy = new GrailsEntityDirtinessStrategy()
        def nativeSession = sessionFactory.getCurrentSession()
        def sessionImplementor = nativeSession as SessionImplementor
        def persister = sessionImplementor.getSessionFactory().getRuntimeMetamodels().getMappingMetamodel().getEntityDescriptor(DSBook) as EntityPersister
        def context = Mock(CustomEntityDirtinessStrategy.DirtyCheckContext)
        
        def book = new DSBook(title: "B1").save(flush: true)
        
        when:
        book.title = "B2" // mark as changed
        strategy.findDirty(book, persister, nativeSession, context)

        then:
        1 * context.doDirtyChecking(_) >> { args ->
            def checker = args[0] as CustomEntityDirtinessStrategy.AttributeChecker
            def info = Mock(CustomEntityDirtinessStrategy.AttributeInformation)
            info.getName() >> "lastUpdated"
            assert checker.isDirty(info) == true
        }
    }

    def "resetDirty tracks changes"() {
        given:
        def strategy = new GrailsEntityDirtinessStrategy()
        def nativeSession = sessionFactory.getCurrentSession()
        def book = new DSBook(title: "B1")
        book.title = "B2"
        assert book.hasChanged()

        when:
        strategy.resetDirty(book, null, nativeSession)

        then:
        !book.hasChanged()
    }
}

@Entity
class DSBook implements HibernateEntity<DSBook> {
    Long id
    String title
    Date lastUpdated
    DSEmbeddedEntity embeddedProp
    static embedded = ['embeddedProp']
    static constraints = {
        title nullable: true
        lastUpdated nullable: true
        embeddedProp nullable: true
    }
}

@Entity
class DSEmbeddedEntity implements HibernateEntity<DSEmbeddedEntity> {
    Long id
    String name
    static constraints = {
        name nullable: true
    }
}
