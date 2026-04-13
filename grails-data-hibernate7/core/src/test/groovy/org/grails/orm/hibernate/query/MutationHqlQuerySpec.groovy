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
package org.grails.orm.hibernate.query

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.FlushMode
import org.grails.datastore.mapping.query.Query

class MutationHqlQuerySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([MutationHqlQuerySpecBook])
    }

    def setup() {
        new MutationHqlQuerySpecBook(title: "The Hobbit", pages: 310).save()
        new MutationHqlQuerySpecBook(title: "Fellowship", pages: 423).save(flush: true)
    }

    private Query buildMutationQuery(CharSequence hql, Map namedParams = [:], Collection positionalParams = null) {
        def entity = mappingContext.getPersistentEntity(MutationHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, hql, namedParams, positionalParams, [:], false, true)
        def session = sessionFactory.currentSession
        HibernateHqlQuery.buildQuery(session, datastore, sessionFactory, entity, ctx)
    }

    void "executeUpdate with named parameters updates correctly"() {
        when:
        int updated = buildMutationQuery("update MutationHqlQuerySpecBook set pages = :p where title = :t", [p: 999, t: "The Hobbit"]).executeUpdate()
        MutationHqlQuerySpecBook.withSession { it.clear() }
        
        then:
        updated == 1
        MutationHqlQuerySpecBook.findByTitle("The Hobbit").pages == 999
    }

    void "executeUpdate with positional parameters updates correctly"() {
        when:
        // Pass positionalParams explicitly to force ordinal parameters (?1, ?2)
        int updated = buildMutationQuery("update MutationHqlQuerySpecBook set pages = ?1 where title = ?2", [:], [1000, "Fellowship"]).executeUpdate()
        MutationHqlQuerySpecBook.withSession { it.clear() }
        
        then:
        updated == 1
        MutationHqlQuerySpecBook.findByTitle("Fellowship").pages == 1000
    }

    void "executeUpdate with GString updates correctly"() {
        given:
        int newPages = 111
        String title = "The Hobbit"
        
        when:
        // By default GString uses named parameters (p0, p1, etc.)
        int updated = buildMutationQuery("update MutationHqlQuerySpecBook set pages = ${newPages} where title = ${title}").executeUpdate()
        MutationHqlQuerySpecBook.withSession { it.clear() }
        
        then:
        updated == 1
        MutationHqlQuerySpecBook.findByTitle("The Hobbit").pages == 111
    }

    void "executeUpdate with GString and positional parameters updates correctly"() {
        given:
        int newPages = 444
        String title = "Fellowship"
        
        when:
        // Pass an empty collection to opt-in to positional expansion (?1, ?2, etc.)
        int updated = buildMutationQuery("update MutationHqlQuerySpecBook set pages = ${newPages} where title = ${title}", [:], []).executeUpdate()
        MutationHqlQuerySpecBook.withSession { it.clear() }
        
        then:
        updated == 1
        MutationHqlQuerySpecBook.findByTitle("Fellowship").pages == 444
    }

    void "list() throws UnsupportedOperationException"() {
        when:
        buildMutationQuery("update MutationHqlQuerySpecBook set pages = 1").list()
        
        then:
        thrown(UnsupportedOperationException)
    }

    void "singleResult() throws UnsupportedOperationException"() {
        when:
        buildMutationQuery("update MutationHqlQuerySpecBook set pages = 1").singleResult()
        
        then:
        thrown(UnsupportedOperationException)
    }

    void "executeQuery() throws UnsupportedOperationException"() {
        when:
        def query = buildMutationQuery("update MutationHqlQuerySpecBook set pages = 1")
        query.executeQuery(mappingContext.getPersistentEntity(MutationHqlQuerySpecBook.name), null)
        
        then:
        thrown(UnsupportedOperationException)
    }

    void "populateQuerySettings for MutationHqlQuery"() {
        given:
        def query = buildMutationQuery("update MutationHqlQuerySpecBook set pages = 1")
        
        when:
        query.populateQuerySettings([flushMode: FlushMode.COMMIT], null)
        
        then:
        noExceptionThrown()
    }

    void "populateQueryWithNamedArguments for MutationHqlQuery"() {
        given:
        def query = buildMutationQuery("update MutationHqlQuerySpecBook set pages = :p where title = :t")
        
        when:
        query.populateQueryWithNamedArguments([p: 222, t: "The Hobbit"])
        int updated = query.executeUpdate()
        MutationHqlQuerySpecBook.withSession { it.clear() }
        
        then:
        updated == 1
        MutationHqlQuerySpecBook.findByTitle("The Hobbit").pages == 222
    }

    void "populateQueryWithIndexedArguments for MutationHqlQuery"() {
        given:
        def query = buildMutationQuery("update MutationHqlQuerySpecBook set pages = ?1 where title = ?2")
        
        when:
        query.populateQueryWithIndexedArguments([333, "Fellowship"])
        int updated = query.executeUpdate()
        MutationHqlQuerySpecBook.withSession { it.clear() }
        
        then:
        updated == 1
        MutationHqlQuerySpecBook.findByTitle("Fellowship").pages == 333
    }

    void "selectQuery returns null for MutationHqlQuery"() {
        expect:
        buildMutationQuery("update MutationHqlQuerySpecBook set pages = 1").selectQuery() == null
    }
}

@Entity
class MutationHqlQuerySpecBook {
    String title
    Integer pages
}
