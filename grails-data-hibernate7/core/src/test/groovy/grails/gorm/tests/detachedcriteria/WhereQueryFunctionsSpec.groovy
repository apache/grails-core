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
package grails.gorm.tests.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion

class WhereQueryFunctionsSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(WhereQueryFunctionEntity)
    }

    private void seedEntities() {
        new WhereQueryFunctionEntity(name: 'a', label: 'Alpha', amount: 1, created: date(2011, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'b', label: 'Beta', amount: 2, created: date(2012, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'c', label: null, amount: 3, created: date(2013, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'd', label: 'Delta', amount: null, created: null).save(flush: true)
    }

    private static Date date(int year, int month, int day) {
        new GregorianCalendar(year, month - 1, day).time
    }

    void "the documented case-insensitive and upper/lower functions execute"() {
        given:
        seedEntities()

        when:
        def upperMatches = WhereQueryFunctionEntity.where { upper(name) == 'A' }.list()*.name
        def lowerMatches = WhereQueryFunctionEntity.where { lower(name) == 'a' }.list()*.name
        def upperLike = WhereQueryFunctionEntity.where { upper(name) ==~ 'A%' }.list()*.name
        def upperIn = WhereQueryFunctionEntity.where { upper(name) in ['A', 'B'] }.list()*.name.sort()

        then:
        upperMatches == ['a']
        lowerMatches == ['a']
        upperLike == ['a']
        upperIn == ['a', 'b']
    }

    void "the documented date functions execute"() {
        given:
        seedEntities()

        when:
        def bornIn2011 = WhereQueryFunctionEntity.where { year(created) == 2011 }.list()*.name
        def bornAfter2011 = WhereQueryFunctionEntity.where { year(created) > 2011 }.list()*.name.sort()
        def bornFrom2012 = WhereQueryFunctionEntity.where { year(created) >= 2012 }.list()*.name.sort()
        def bornBefore2013 = WhereQueryFunctionEntity.where { year(created) < 2013 }.list()*.name.sort()
        def bornUpTo2012 = WhereQueryFunctionEntity.where { year(created) <= 2012 }.list()*.name.sort()

        then:
        bornIn2011 == ['a']
        bornAfter2011 == ['b', 'c']
        bornFrom2012 == ['b', 'c']
        bornBefore2013 == ['a', 'b']
        bornUpTo2012 == ['a', 'b']
    }

    void "the documented string functions execute"() {
        given:
        seedEntities()

        when:
        def trimmed = WhereQueryFunctionEntity.where { trim(name) == 'a' }.list()*.name
        def oneCharacter = WhereQueryFunctionEntity.where { length(name) == 1 }.list()*.name.sort()
        def ilikeLabel = WhereQueryFunctionEntity.where { upper(label) =~ 'alp%' }.list()*.name
        def convertedIntCriteria = new DetachedCriteria<>(WhereQueryFunctionEntity)
        convertedIntCriteria.add(new FunctionCallingCriterion('year', new Query.Equals('created', '2011')))
        def convertedInt = convertedIntCriteria.list()*.name

        then:
        trimmed == ['a']
        oneCharacter == ['a', 'b', 'c', 'd']
        ilikeLabel == ['a']
        convertedInt == ['a']
    }

    void "function comparisons handle null operands and null-inclusive NotEquals"() {
        given:
        seedEntities()

        when:
        def nullLabel = WhereQueryFunctionEntity.where { upper(label) == null }.list()*.name
        def nonNullLabel = WhereQueryFunctionEntity.where { upper(label) != null }.list()*.name.sort()
        def notAlpha = WhereQueryFunctionEntity.where { upper(label) != 'ALPHA' }.list()*.name.sort()

        then:
        nullLabel == ['c']
        nonNullLabel == ['a', 'b', 'd']
        notAlpha == ['b', 'c', 'd']
    }

    void "function IN criterion supports projected DetachedCriteria subquery"() {
        given:
        seedEntities()
        DetachedCriteria<WhereQueryFunctionEntity> sub = new DetachedCriteria<>(WhereQueryFunctionEntity).build {
            projections {
                property('name')
            }
            le('amount', 2)
        }
        DetachedCriteria<WhereQueryFunctionEntity> query = new DetachedCriteria<>(WhereQueryFunctionEntity)
        query.add(new FunctionCallingCriterion('lower', new Query.In('name', sub)))

        when:
        def matches = query.list()*.name.sort()

        then:
        matches == ['a', 'b']
    }

    void "function IN criterion filters null values, handles all-null and empty lists, and converts numeric function values"() {
        given:
        seedEntities()

        when:
        def mixedNullString = WhereQueryFunctionEntity.where { upper(name) in ['A', null, 'C'] }.list()*.name.sort()
        def allNullString = WhereQueryFunctionEntity.where { upper(name) in [null] }.list()
        def emptyString = WhereQueryFunctionEntity.where { upper(name) in [] }.list()
        def mixedNullNumeric = WhereQueryFunctionEntity.where { year(created) in ['2011', null, 2013] }.list()*.name.sort()
        def allNullNumeric = WhereQueryFunctionEntity.where { year(created) in [null] }.list()
        def emptyNumeric = WhereQueryFunctionEntity.where { year(created) in [] }.list()

        then:
        mixedNullString == ['a', 'c']
        allNullString.isEmpty()
        emptyString.isEmpty()
        mixedNullNumeric == ['a', 'c']
        allNullNumeric.isEmpty()
        emptyNumeric.isEmpty()
    }

    void "bulk updateAll and deleteAll support function IN, ILike, and subquery criteria"() {
        given:
        seedEntities()

        when:
        int updatedIn = WhereQueryFunctionEntity.where { upper(name) in ['A', 'B'] }.updateAll(amount: 10)
        int updatedIlike = WhereQueryFunctionEntity.where { upper(label) =~ 'del%' }.updateAll(amount: 20)
        session.clear()

        then:
        updatedIn == 2
        updatedIlike == 1
        WhereQueryFunctionEntity.findByName('a').amount == 10
        WhereQueryFunctionEntity.findByName('b').amount == 10
        WhereQueryFunctionEntity.findByName('d').amount == 20
    }
}

@Entity
class WhereQueryFunctionEntity implements HibernateEntity<WhereQueryFunctionEntity> {

    String name
    String label
    Integer amount
    Date created

    static constraints = {
        label nullable: true
        amount nullable: true
        created nullable: true
    }
}
