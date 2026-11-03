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
package org.grails.datastore.mapping.query

import grails.gorm.annotation.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.FlushModeType
import jakarta.persistence.LockModeType
import jakarta.persistence.criteria.JoinType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.query.api.AssociationCriteria
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent

class QuerySpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext('test')
    PersistentEntity bookEntity = mappingContext.addPersistentEntity(QSBook)
    PersistentEntity authorEntity = mappingContext.addPersistentEntity(QSAuthor)
    ApplicationEventPublisher publisher = null
    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> mappingContext
        getApplicationEventPublisher() >> { publisher }
    }
    Session session = Mock(Session) {
        getDatastore() >> datastore
        getFlushMode() >> FlushModeType.AUTO
        getMappingContext() >> mappingContext
    }
    TestQuery query = new TestQuery(session, bookEntity)

    void "a new query exposes its session, entity and empty settings"() {
        expect:
        query.session.is(session)
        query.entity.is(bookEntity)
        query.max == null
        query.offset == null
        query.orderBy.empty
        query.criteria instanceof Query.Conjunction
        query.criteria.empty
        query.projections().empty
    }

    void "pagination, ordering and fetch settings are fluent"() {
        when:
        Query result = query.max(5).offset(2).order(Query.Order.desc('title')).order(null)
                .join('author').join('tags', JoinType.LEFT).select('other').cache(true).lock(true)

        then:
        result.is(query)
        query.max == 5
        query.offset == 2
        query.orderBy*.property == ['title']
        query.fetchStrategies == [author: FetchType.EAGER, tags: FetchType.EAGER, other: FetchType.LAZY]
        query.joinTypes == [tags: JoinType.LEFT]
        query.queryCache
        query.lockResult == LockModeType.PESSIMISTIC_WRITE

        when:
        query.maxResults(7).firstResult(3).lock(LockModeType.OPTIMISTIC).clearOrders()

        then:
        query.max == 7
        query.offset == 3
        query.lockResult == LockModeType.OPTIMISTIC
        query.orderBy.empty
    }

    void "fetch strategies fall back to the mapping and then to lazy"() {
        expect:
        query.fetchStrategy('author') == FetchType.LAZY
        query.fetchStrategy('missing') == FetchType.LAZY

        when:
        query.join('author')

        then:
        query.fetchStrategy('author') == FetchType.EAGER
    }

    @Unroll
    void "#method adds a #type.simpleName criterion"() {
        when:
        Query result = query."$method"(*args)

        then:
        result.is(query)
        query.criteria.criteria.size() == 1
        type.isInstance(query.criteria.criteria[0])
        query.criteria.criteria[0].property == args[0]

        where:
        method       | args               | type
        'eq'         | ['title', 't']     | Query.Equals
        'isEmpty'    | ['title']          | Query.IsEmpty
        'isNotEmpty' | ['title']          | Query.IsNotEmpty
        'isNull'     | ['title']          | Query.IsNull
        'isNotNull'  | ['title']          | Query.IsNotNull
        'gt'         | ['pages', 1]       | Query.GreaterThan
        'gte'        | ['pages', 1]       | Query.GreaterThanEquals
        'ge'         | ['pages', 1]       | Query.GreaterThanEquals
        'lte'        | ['pages', 1]       | Query.LessThanEquals
        'le'         | ['pages', 1]       | Query.LessThanEquals
        'lt'         | ['pages', 1]       | Query.LessThan
        'in'         | ['title', ['a']]   | Query.In
        'between'    | ['pages', 1, 2]    | Query.Between
        'like'       | ['title', 'a%']    | Query.Like
        'ilike'      | ['title', 'a%']    | Query.ILike
        'rlike'      | ['title', 'a.*']   | Query.RLike
    }

    void "equality criteria resolve persistent entity values to their identifiers"() {
        given:
        QSAuthor author = new QSAuthor(id: 7L, name: 'a')
        QSAddress address = new QSAddress(street: 's')

        when:
        query.eq('author', author)
        query.eq('address', address)
        query.idEq(author)
        query.allEq([title: 't', author: author])

        then:
        query.criteria.criteria[0].value == 7L
        query.criteria.criteria[1].value.is(address)
        query.criteria.criteria[2] instanceof Query.IdEquals
        query.criteria.criteria[2].value == 7L
        query.criteria.criteria[3] instanceof Query.Conjunction
        query.criteria.criteria[3].criteria*.value == ['t', 7L]
    }

    void "proxies are unwrapped to their identifier when resolving values"() {
        given:
        ProxyFactory proxyFactory = Mock(ProxyFactory)
        mappingContext.proxyFactory = proxyFactory

        when:
        query.eq('author', 'proxy')

        then:
        1 * proxyFactory.isProxy('proxy') >> true
        1 * proxyFactory.getIdentifier('proxy') >> 42L
        query.criteria.criteria[0].value == 42L
    }

    void "and, or and junctions nest criteria"() {
        given:
        Query.Criterion a = Restrictions.eq('title', 'a')
        Query.Criterion b = Restrictions.eq('title', 'b')

        when:
        query.and(a, b)
        query.or(a, b)
        Query.Junction disjunction = query.disjunction()
        Query.Junction conjunction = query.conjunction()
        Query.Junction negation = query.negation()

        then:
        query.criteria.criteria[0] instanceof Query.Conjunction
        query.criteria.criteria[0].criteria == [a, b]
        query.criteria.criteria[1] instanceof Query.Disjunction
        query.criteria.criteria[1].criteria == [a, b]
        disjunction instanceof Query.Disjunction
        conjunction instanceof Query.Conjunction
        negation instanceof Query.Negation
        query.criteria.criteria[2..4] == [disjunction, conjunction, negation]

        when:
        query.and(null, b)

        then:
        thrown(IllegalArgumentException)

        when:
        query.or(a, null)

        then:
        thrown(IllegalArgumentException)
    }

    void "adding criteria resolves values and copies nested junctions"() {
        given:
        QSAuthor author = new QSAuthor(id: 3L)
        Query.Disjunction nested = new Query.Disjunction([Restrictions.eq('author', author), new Query.Negation().add(Restrictions.isNull('title'))])

        when:
        query.add(nested)
        query.add(Restrictions.eq('author', author))

        then:
        Query.Disjunction copied = query.criteria.criteria[0]
        copied.criteria[0].value == 3L
        copied.criteria[1] instanceof Query.Negation
        copied.criteria[1].criteria[0] instanceof Query.IsNull
        query.criteria.criteria[1].value == 3L
    }

    void "association criteria are expanded into association queries"() {
        given:
        Association association = (Association) bookEntity.getPropertyByName('author')
        AssociationCriteria criteria = Stub(Query.Criterion, additionalInterfaces: [AssociationCriteria]) {
            getAssociation() >> association
            getCriteria() >> [Restrictions.eq('name', 'n')]
        }

        when:
        query.add(criteria)

        then:
        AssociationQuery associationQuery = query.criteria.criteria[0]
        associationQuery.association.is(association)
        associationQuery.entity.is(authorEntity)
        associationQuery.criteria.criteria[0].value == 'n'

        when:
        associationQuery.list()

        then:
        thrown(UnsupportedOperationException)
    }

    void "association queries can only be created for associations"() {
        expect:
        query.createQuery('author').association.name == 'author'

        when:
        query.createQuery('title')

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message == 'Cannot query association [title] of class [' + QSBook.name + ']. The specified property is not an association.'

        when:
        query.createQuery('missing')

        then:
        thrown(InvalidDataAccessResourceUsageException)
    }

    void "list flushes an auto flush session, executes and returns the results"() {
        given:
        query.results = ['a', 'b']

        when:
        List results = query.list()

        then:
        1 * session.flush()
        results == ['a', 'b']
        query.executed == [query.criteria]
        !query.uniqueResult
    }

    void "list does not flush when the session is not in auto flush mode"() {
        given:
        Session commitSession = Stub(Session) {
            getDatastore() >> datastore
            getFlushMode() >> FlushModeType.COMMIT
        }
        TestQuery commitQuery = new TestQuery(commitSession, bookEntity)

        when:
        commitQuery.list()

        then:
        commitQuery.executed.size() == 1
    }

    void "singleResult returns the first result or null and marks the query unique"() {
        given:
        query.results = []

        expect:
        query.singleResult() == null
        query.uniqueResult

        when:
        query.results = ['x', 'y']

        then:
        query.singleResult() == 'x'
    }

    void "query events are published around execution and can replace the results"() {
        given:
        List<Object> events = []
        publisher = Mock(ApplicationEventPublisher) {
            publishEvent(_) >> { List args ->
                Object e = args[0]
                events << e
                if (e instanceof PostQueryEvent) {
                    e.results = ['replaced']
                }
            }
        }
        query.results = ['original']

        when:
        List results = query.list()

        then:
        results == ['replaced']
        events.size() == 2
        events[0] instanceof PreQueryEvent
        events[0].query.is(query)
        events[0].source.is(datastore)
        events[1] instanceof PostQueryEvent
        events[1].results == ['replaced']
    }

    void "countResults uses a count projection unless projections are already present"() {
        given:
        query.results = [3L]

        expect:
        query.countResults() == 3L
        query.projections().projectionList*.class == [Query.CountProjection]

        when:
        TestQuery projected = new TestQuery(session, bookEntity)
        projected.projections().property('title')
        projected.results = ['a', 'b', 'c']

        then:
        projected.countResults() == 3
    }

    void "cloning copies the criteria into a new query from the session"() {
        given:
        TestQuery copy = new TestQuery(session, bookEntity)
        query.eq('title', 't')

        when:
        Object cloned = query.clone()

        then:
        1 * session.createQuery(QSBook) >> copy
        cloned.is(copy)
        copy.criteria.criteria[0].value == 't'

        when:
        new TestQuery(null, bookEntity).clone()

        then:
        thrown(IllegalStateException)
    }

    @Unroll
    void "patternToRegex(#pattern) == #expected"() {
        expect:
        Query.patternToRegex(pattern) == expected

        where:
        pattern | expected
        'a%b'   | '^\\Qa\\E.*\\Qb\\E$'
        '%x'    | '^\\Q\\E.*\\Qx\\E$'
        'x%'    | '^\\Qx\\E.*\\Q\\E$'
        'x'     | '^\\Qx\\E$'
        null    | '^\\Qnull\\E$'
    }

    void "the deprecated unique result setter is retained"() {
        when:
        query.setUniqueResult(true)

        then:
        query.uniqueResult
    }

    void "orders carry a property, a direction and the ignore case flag"() {
        expect:
        Query.Order.asc('a').direction == Query.Order.Direction.ASC
        Query.Order.desc('a').direction == Query.Order.Direction.DESC
        new Query.Order('a').direction == Query.Order.Direction.ASC
        new Query.Order('a').property == 'a'
        !new Query.Order('a').ignoreCase
        new Query.Order('a').ignoreCase().ignoreCase
    }

    void "junctions ignore null criteria"() {
        given:
        Query.Junction junction = new Query.Conjunction()

        expect:
        junction.empty
        junction.add(null).is(junction)
        junction.empty
        junction.add(Restrictions.isNull('a')).criteria.size() == 1
        !junction.empty
        new Query.Negation().criteria.empty
        new Query.Disjunction([Restrictions.isNull('a')]).criteria.size() == 1
    }

    void "in criteria normalise char sequences and guard their values"() {
        given:
        Query.In inValues = new Query.In('title', [new StringBuilder('a'), 'b'])
        QueryableCriteria subquery = Stub(QueryableCriteria)
        Query.In inSubquery = new Query.In('title', subquery)

        expect:
        inValues.values.toList() == ['a', 'b']
        inValues.values.every { it instanceof String }
        inValues.name == 'title'
        inValues.subquery == null
        new Query.In('title', ['plain']).value == ['plain']
        inSubquery.subquery.is(subquery)
        inSubquery.values.empty

        when:
        inValues.values.add('c')

        then:
        thrown(UnsupportedOperationException)
    }

    void "subquery and property criteria expose their parts"() {
        given:
        QueryableCriteria subquery = Stub(QueryableCriteria)

        expect:
        new Query.NotIn('a', subquery).subquery.is(subquery)
        new Query.NotIn('a', subquery).name == 'a'
        new Query.Exists(subquery).subquery.is(subquery)
        new Query.NotExists(subquery).subquery.is(subquery)
        new Query.EqualsAll('a', subquery).value.is(subquery)
        new Query.Between('a', 1, 2).from == 1
        new Query.Between('a', 1, 2).to == 2
        new Query.Between('a', 1, 2).property == 'a'
        new Query.Between('a', 1, 2).value == 1
        new Query.Like('a', 'x%').pattern == 'x%'
        new Query.RLike('a', 'x.*').pattern == 'x.*'
        new Query.EqualsProperty('a', 'b').otherProperty == 'b'
        new Query.SizeEquals('a', 2).value == 2
        new Query.IdEquals(5).property == 'id'

        when:
        Query.Equals equals = new Query.Equals('a', 1)
        Query.IdEquals idEquals = new Query.IdEquals(1)
        Query.NotEquals notEquals = new Query.NotEquals('a', 1)
        equals.value = 2
        idEquals.value = 2
        notEquals.value = 2

        then:
        equals.value == 2
        idEquals.value == 2
        notEquals.value == 2
    }

    void "projection lists collect projections through the fluent api"() {
        given:
        Query.ProjectionList projections = new Query.ProjectionList()

        when:
        projections.id().count().countDistinct('a').groupProperty('b').distinct().distinct('c').rowCount()
                .property('d').sum('e').min('f').max('g').avg('h')

        then:
        projections.projectionList*.class == [Query.IdProjection, Query.CountProjection, Query.CountDistinctProjection,
                                              Query.GroupPropertyProjection, Query.DistinctProjection, Query.DistinctPropertyProjection,
                                              Query.CountProjection, Query.PropertyProjection, Query.SumProjection, Query.MinProjection,
                                              Query.MaxProjection, Query.AvgProjection]
        projections.projectionList.findAll { it instanceof Query.PropertyProjection }*.propertyName == ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']
        !projections.empty

        when:
        projections.projectionList.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    static class TestQuery extends Query {

        List results = []
        List<Query.Junction> executed = []

        TestQuery(Session session, PersistentEntity entity) {
            super(session, entity)
        }

        @Override
        protected List executeQuery(PersistentEntity entity, Query.Junction criteria) {
            executed << criteria
            results
        }
    }
}

@Entity
class QSBook {
    Long id
    String title
    Integer pages
    QSAuthor author
    QSAddress address
    Set tags
    static belongsTo = [author: QSAuthor]
    static embedded = ['address']
    static hasMany = [tags: QSTag]
}

@Entity
class QSAuthor {
    Long id
    String name
    Set books
    static hasMany = [books: QSBook]
}

@Entity
class QSTag {
    Long id
    String name
}

class QSAddress {
    String street
}
