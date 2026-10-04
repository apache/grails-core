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
package org.grails.datastore.mapping.query.jpa

import grails.gorm.annotation.Entity
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.conversion.DefaultConversionService
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.mapping.query.api.AssociationCriteria
import org.grails.datastore.mapping.query.api.QueryableCriteria

class JpaQueryBuilderSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity book = mappingContext.addPersistentEntity(JqBook)

    @Shared
    PersistentEntity author = mappingContext.addPersistentEntity(JqAuthor)

    private static final String FROM = ' FROM ' + JqBook.name + ' AS jqBook'

    private JpaQueryBuilder builder(List<Query.Criterion> criteria, Query.ProjectionList projections = new Query.ProjectionList(), List<Query.Order> orders = []) {
        new JpaQueryBuilder(book, criteria, projections, orders)
    }

    void "a builder requires an entity"() {
        when:
        new JpaQueryBuilder(null, new Query.Conjunction())

        then:
        ConfigurationException e = thrown()
        e.message == 'No persistent entity specified for JPA query builder'
    }

    void "a builder can be created from queryable criteria"() {
        given:
        QueryableCriteria criteria = Stub(QueryableCriteria) {
            getPersistentEntity() >> book
            getCriteria() >> [Restrictions.eq('title', 't')]
        }

        when:
        JpaQueryInfo info = new JpaQueryBuilder(criteria).buildSelect()

        then:
        info.query == 'SELECT DISTINCT jqBook' + FROM + ' WHERE (jqBook.title=:p1)'
        info.parameters == ['t']
    }

    void "select without criteria selects the distinct entity"() {
        expect:
        builder([]).buildSelect().query == 'SELECT DISTINCT jqBook' + FROM
        builder([]).buildSelect().parameters == null
    }

    void "projections render into the select clause"() {
        given:
        Query.ProjectionList projections = new Query.ProjectionList()
        projections.count().id().property('title').avg('pages').sum('pages').min('pages').max('pages').countDistinct('title')

        expect:
        builder([], projections).buildSelect().query ==
                'SELECT COUNT(jqBook),jqBook.id,jqBook.title,AVG(jqBook.pages),SUM(jqBook.pages),MIN(jqBook.pages),MAX(jqBook.pages),COUNT(DISTINCT jqBook.title)' + FROM
    }

    void "orders are appended to the select"() {
        expect:
        builder([], new Query.ProjectionList(), [Query.Order.asc('title'), Query.Order.desc('pages')]).buildSelect().query ==
                'SELECT DISTINCT jqBook' + FROM + ' ORDER BY jqBook.title ASC jqBook.pages DESC '
    }

    @Unroll
    void "#label renders as '#where'"() {
        when:
        JpaQueryInfo info = builder(criteria).buildSelect()

        then:
        info.query == 'SELECT DISTINCT jqBook' + FROM + ' WHERE (' + where + ')'
        info.parameters == parameters

        where:
        label            | criteria                                                        | where                                                        | parameters
        'eq'             | [Restrictions.eq('title', 't')]                                 | 'jqBook.title=:p1'                                           | ['t']
        'id eq'          | [Restrictions.idEq(5L)]                                         | 'jqBook.id=:p1'                                              | [5L]
        'ne'             | [Restrictions.ne('title', 't')]                                 | 'jqBook.title != :p1'                                        | ['t']
        'gt'             | [Restrictions.gt('pages', 1)]                                   | 'jqBook.pages > :p1'                                         | [1]
        'gte'            | [Restrictions.gte('pages', 1)]                                  | 'jqBook.pages >= :p1'                                        | [1]
        'lt'             | [Restrictions.lt('pages', 1)]                                   | 'jqBook.pages < :p1'                                         | [1]
        'lte'            | [Restrictions.lte('pages', 1)]                                  | 'jqBook.pages <= :p1'                                        | [1]
        'like'           | [Restrictions.like('title', 'a%')]                              | 'jqBook.title like :p1'                                      | ['a%']
        'ilike'          | [Restrictions.ilike('title', 'a%')]                             | 'lower(jqBook.title) like lower(:p1)'                        | ['a%']
        'between'        | [Restrictions.between('pages', 1, 5)]                           | '(jqBook.pages >= :p1 AND jqBook.pages <= :p2)'              | [1, 5]
        'in'             | [Restrictions.in('title', ['a', 'b'])]                          | 'jqBook.title IN (:p1,:p2)'                                  | ['a', 'b']
        'is null'        | [Restrictions.isNull('title')]                                  | 'jqBook.title IS NULL '                                      | []
        'is not null'    | [Restrictions.isNotNull('title')]                               | 'jqBook.title IS NOT NULL '                                  | []
        'is empty'       | [Restrictions.isEmpty('title')]                                 | 'jqBook.title IS EMPTY '                                     | []
        'is not empty'   | [Restrictions.isNotEmpty('title')]                              | 'jqBook.title IS NOT EMPTY '                                 | []
        'size eq'        | [Restrictions.sizeEq('tags', 2)]                                | 'SIZE(jqBook.tags) =:p1'                                     | [2]
        'size ne'        | [Restrictions.sizeNe('tags', 2)]                                | 'SIZE(jqBook.tags) !=:p1'                                    | [2]
        'size gt'        | [Restrictions.sizeGt('tags', 2)]                                | 'SIZE(jqBook.tags) >:p1'                                     | [2]
        'size ge'        | [Restrictions.sizeGe('tags', 2)]                                | 'SIZE(jqBook.tags) >=:p1'                                    | [2]
        'size lt'        | [Restrictions.sizeLt('tags', 2)]                                | 'SIZE(jqBook.tags) <:p1'                                     | [2]
        'size le'        | [Restrictions.sizeLe('tags', 2)]                                | 'SIZE(jqBook.tags) <=:p1'                                    | [2]
        'eq property'    | [Restrictions.eqProperty('title', 'subtitle')]                  | 'jqBook.title=jqBook.subtitle'                               | []
        'ne property'    | [Restrictions.neProperty('title', 'subtitle')]                  | 'jqBook.title!=jqBook.subtitle'                              | []
        'gt property'    | [Restrictions.gtProperty('pages', 'chapters')]                  | 'jqBook.pages>jqBook.chapters'                               | []
        'ge property'    | [Restrictions.geProperty('pages', 'chapters')]                  | 'jqBook.pages>=jqBook.chapters'                              | []
        'lt property'    | [Restrictions.ltProperty('pages', 'chapters')]                  | 'jqBook.pages<jqBook.chapters'                               | []
        'le property'    | [Restrictions.leProperty('pages', 'chapters')]                  | 'jqBook.pages<=jqBook.chapters'                              | []
        'two criteria'   | [Restrictions.eq('title', 't'), Restrictions.gt('pages', 1)]    | 'jqBook.title=:p1 AND jqBook.pages > :p2'                    | ['t', 1]
        'disjunction'    | [Restrictions.or(Restrictions.eq('title', 'a'), Restrictions.eq('title', 'b'))] | '(jqBook.title=:p1 OR jqBook.title=:p2)'      | ['a', 'b']
        'conjunction'    | [Restrictions.and(Restrictions.eq('title', 'a'), Restrictions.eq('title', 'b'))] | '(jqBook.title=:p1 AND jqBook.title=:p2)'    | ['a', 'b']
        'negation'       | [new Query.Negation().add(Restrictions.eq('title', 'a'))]       | ' NOT(jqBook.title=:p1)'                                     | ['a']
    }

    void "values are converted to the property type"() {
        given:
        JpaQueryBuilder converting = builder([Restrictions.eq('pages', '12')])
        converting.conversionService = new DefaultConversionService()

        expect:
        converting.buildSelect().parameters == [12]
    }

    void "criteria on unknown properties are rejected"() {
        when:
        builder([Restrictions.eq('missing', 't')]).buildSelect()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message == 'Cannot use [Equals] criterion on non-existent property: missing'
    }

    void "unsupported criteria are rejected"() {
        when:
        builder([new Query.Criterion() { }]).buildSelect()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.startsWith('Queries of type ')
        e.message.endsWith(' are not supported by this implementation')
    }

    void "to-one association criteria use dotted paths and to-many use joins"() {
        given:
        Association toOne = (Association) book.getPropertyByName('author')
        Association toMany = (Association) author.getPropertyByName('books')
        AssociationCriteria authorCriteria = Stub(Query.Criterion, additionalInterfaces: [AssociationCriteria]) {
            getAssociation() >> toOne
            getCriteria() >> [Restrictions.eq('name', 'n')]
        }
        AssociationCriteria bookCriteria = Stub(Query.Criterion, additionalInterfaces: [AssociationCriteria]) {
            getAssociation() >> toMany
            getCriteria() >> [Restrictions.eq('title', 't')]
        }

        expect:
        builder([authorCriteria]).buildSelect().query == 'SELECT DISTINCT jqBook' + FROM + ' WHERE (jqBook.author.name=:p1)'
        new JpaQueryBuilder(author, [bookCriteria]).buildSelect().query ==
                'SELECT DISTINCT jqAuthor FROM ' + JqAuthor.name + ' AS jqAuthor INNER JOIN jqAuthor.books books WHERE (books.title=:p1)'
    }

    void "association queries are rejected for updates and deletes"() {
        given:
        Association toOne = (Association) book.getPropertyByName('author')
        AssociationQuery associationQuery = new AssociationQuery(null, author, toOne)
        associationQuery.add(Restrictions.eq('name', 'n'))

        expect:
        builder([associationQuery]).buildSelect().query == 'SELECT DISTINCT jqBook' + FROM + ' WHERE (jqBook.author.name=:p1)'

        when:
        builder([associationQuery]).buildDelete()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message == 'Joins cannot be used in a DELETE or UPDATE operation'
    }

    void "subqueries are rendered inline"() {
        given:
        QueryableCriteria subquery = Stub(QueryableCriteria) {
            getPersistentEntity() >> author
            getCriteria() >> [Restrictions.eq('name', 'n')]
            getProjections() >> [new Query.IdProjection()]
        }

        expect:
        builder([Restrictions.in('author', subquery)]).buildSelect().query ==
                'SELECT DISTINCT jqBook' + FROM + ' WHERE (jqBook.author IN (SELECT jqAuthor0.id FROM ' + JqAuthor.name + ' jqAuthor0 WHERE jqAuthor0.name=:p1))'
        builder([Restrictions.notIn('author', subquery)]).buildSelect().query.contains('jqBook.author NOT IN (SELECT')
        builder([new Query.EqualsAll('pages', subquery)]).buildSelect().query.contains('jqBook.pages = ALL (SELECT')
        builder([new Query.NotEqualsAll('pages', subquery)]).buildSelect().query.contains('jqBook.pages != ALL (SELECT')
        builder([new Query.GreaterThanAll('pages', subquery)]).buildSelect().query.contains('jqBook.pages > ALL (SELECT')
        builder([new Query.GreaterThanSome('pages', subquery)]).buildSelect().query.contains('jqBook.pages > SOME (SELECT')
        builder([new Query.GreaterThanEqualsAll('pages', subquery)]).buildSelect().query.contains('jqBook.pages >= ALL (SELECT')
        builder([new Query.GreaterThanEqualsSome('pages', subquery)]).buildSelect().query.contains('jqBook.pages >= SOME (SELECT')
        builder([new Query.LessThanAll('pages', subquery)]).buildSelect().query.contains('jqBook.pages < ALL (SELECT')
        builder([new Query.LessThanSome('pages', subquery)]).buildSelect().query.contains('jqBook.pages < SOME (SELECT')
        builder([new Query.LessThanEqualsAll('pages', subquery)]).buildSelect().query.contains('jqBook.pages <= ALL (SELECT')
        builder([new Query.LessThanEqualsSome('pages', subquery)]).buildSelect().query.contains('jqBook.pages <= SOME (SELECT')
    }

    void "update statements set sorted properties and keep the where clause"() {
        when:
        JpaQueryInfo info = builder([Restrictions.eq('title', 't')]).buildUpdate([title: 'u', pages: 3])

        then:
        info.query == 'UPDATE ' + JqBook.name + ' jqBook SET jqBook.pages=:p1, jqBook.title=:p2 WHERE (jqBook.title=:p3)'
        info.parameters == [3, 'u', 't']

        when:
        builder([]).buildUpdate([:])

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message == 'No properties specified to update'

        when:
        builder([]).buildUpdate([missing: 1])

        then:
        e = thrown()
        e.message == "Property 'missing' of class '" + JqBook.name + "' specified in update does not exist"
    }

    void "delete statements carry the where clause"() {
        expect:
        builder([Restrictions.eq('title', 't')]).buildDelete().query == 'DELETE FROM ' + JqBook.name + ' jqBook WHERE (jqBook.title=:p1)'
        builder([Restrictions.eq('title', 't')]).buildDelete().parameters == ['t']
        builder([]).buildDelete().query == 'DELETE FROM ' + JqBook.name + ' jqBook'
    }

    void "the builder accepts hibernate compatibility and conversion service settings"() {
        given:
        JpaQueryBuilder jpaQueryBuilder = builder([Restrictions.eq('title', 't')])

        when:
        jpaQueryBuilder.hibernateCompatible = true
        jpaQueryBuilder.conversionService = mappingContext.conversionService

        then:
        jpaQueryBuilder.buildSelect().parameters == ['t']
        JpaQueryBuilder.appendCriteriaForOperator(new StringBuilder(), 'x', 'y', 0, '=', false) == 1
    }
}

@Entity
class JqBook {
    Long id
    String title
    String subtitle
    Integer pages
    Integer chapters
    JqAuthor author
    Set tags
    static belongsTo = [author: JqAuthor]
    static hasMany = [tags: JqTag]
}

@Entity
class JqAuthor {
    Long id
    String name
    Set books
    static hasMany = [books: JqBook]
}

@Entity
class JqTag {
    Long id
    String name
}
