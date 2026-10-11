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

import jakarta.persistence.criteria.JoinType

import spock.lang.Specification

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.query.Query

class QueryAliasRegistrarSpec extends Specification {

    JpaQueryContext context = Mock(JpaQueryContext)

    private QueryAliasRegistrar registrar(List<Query.Criterion> criteria, Map<String, JoinType> joinTypes = [:], HibernateQuery query = null) {
        DetachedCriteria<?> detached = Stub(DetachedCriteria) {
            getCriteria() >> criteria
            getJoinTypes() >> joinTypes
        }
        new QueryAliasRegistrar(detached, query)
    }

    void "a join type configured on the criteria is kept for the alias of that path"() {
        when:
        registrar([], [author: JoinType.LEFT]).registerDetachedJoins(context)

        then:
        1 * context.registerAlias('author', new HibernateAlias('author', 'author', JoinType.LEFT))
    }

    void "a dotted property name registers an inner alias for its first segment"() {
        when:
        registrar([new Query.Equals('author.name', 'x')]).discover([new Query.Equals('author.name', 'x')], context)

        then:
        1 * context.hasAlias('author') >> false
        1 * context.registerAlias('author', new HibernateAlias('author', 'author', JoinType.INNER))
    }

    void "an alias the context already knows is not registered again"() {
        when:
        registrar([]).discover([new Query.Equals('author.name', 'x'), new HibernateAlias('tags', 't')], context)

        then:
        1 * context.hasAlias('author') >> true
        1 * context.hasAlias('t') >> true
        0 * context.registerAlias(*_)
    }

    void "an explicit alias is registered with its own definition"() {
        given:
        HibernateAlias alias = new HibernateAlias('tags', 't', JoinType.LEFT)

        when:
        registrar([]).discover([alias], context)

        then:
        1 * context.hasAlias('t') >> false
        1 * context.registerAlias('t', alias)
    }

    void "an association criterion with an alias takes the join type configured for its path"() {
        given:
        DetachedAssociationCriteria<?> association = Stub(DetachedAssociationCriteria) {
            getAlias() >> 'a'
            getAssociationPath() >> 'author'
            getCriteria() >> []
        }

        when:
        registrar([], [author: JoinType.LEFT]).discover([association], context)

        then:
        1 * context.registerAlias('a', new HibernateAlias('author', 'a', JoinType.LEFT))
    }

    void "an association criterion without a configured join type defaults to an inner join"() {
        given:
        DetachedAssociationCriteria<?> association = Stub(DetachedAssociationCriteria) {
            getAlias() >> 'a'
            getAssociationPath() >> 'author'
            getCriteria() >> []
        }

        when:
        registrar([]).discover([association], context)

        then:
        1 * context.registerAlias('a', new HibernateAlias('author', 'a', JoinType.INNER))
    }

    void "aliases are discovered inside junctions"() {
        when:
        registrar([]).discover([new Query.Conjunction().add(new Query.Equals('author.name', 'x'))], context)

        then:
        1 * context.hasAlias('author') >> false
        1 * context.registerAlias('author', new HibernateAlias('author', 'author', JoinType.INNER))
    }

    void "explicit aliases are the query's aliases followed by those among the criteria"() {
        given:
        HibernateAlias fromQuery = new HibernateAlias('a', 'a')
        HibernateAlias fromCriteria = new HibernateAlias('b', 'b')
        HibernateQuery query = Stub(HibernateQuery) { getAliases() >> [fromQuery] }

        expect:
        registrar([new Query.Equals('x', 1), fromCriteria], [:], query).explicitAliases() == [fromQuery, fromCriteria]
    }

    void "explicit aliases tolerate a missing query"() {
        expect:
        registrar([new HibernateAlias('b', 'b')]).explicitAliases() == [new HibernateAlias('b', 'b')]
    }

    void "discovery tolerates null criteria"() {
        when:
        registrar([]).discover(null, context)

        then:
        0 * context._
    }
}
