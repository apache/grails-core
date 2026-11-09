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
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion

class WhereQueryFunctionsSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(WhereQueryFunctionEntity, WhereQueryFunctionOwner, WhereQueryFunctionPet)
    }

    private void seedEntities() {
        new WhereQueryFunctionEntity(name: 'a', label: 'Alpha', amount: 1, created: date(2011, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'b', label: 'Beta', amount: 2, created: date(2012, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'c', label: null, amount: 3, created: date(2013, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'd', label: 'Delta', amount: null, created: null).save(flush: true)
    }

    private static DetachedCriteria<WhereQueryFunctionEntity> criteriaOf(Query.Criterion criterion) {
        DetachedCriteria<WhereQueryFunctionEntity> criteria = new DetachedCriteria<>(WhereQueryFunctionEntity)
        criteria.add(criterion)
        criteria
    }

    private static DetachedCriteria<WhereQueryFunctionEntity> lengthIn(List values) {
        criteriaOf(new FunctionCallingCriterion('length', new Query.In('name', values)))
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

    void "function comparisons handle null operands, and NotEquals leaves out null rows as SQL does"() {
        given:
        seedEntities()

        when:
        def nullLabel = WhereQueryFunctionEntity.where { upper(label) == null }.list()*.name
        def nonNullLabel = WhereQueryFunctionEntity.where { upper(label) != null }.list()*.name.sort()
        def notAlpha = WhereQueryFunctionEntity.where { upper(label) != 'ALPHA' }.list()*.name.sort()

        then:
        nullLabel == ['c']
        nonNullLabel == ['a', 'b', 'd']
        notAlpha == ['b', 'd']
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

    void "deleteAll with a function deletes the rows the function matches"() {
        given:
        seedEntities()

        when:
        int deleted = WhereQueryFunctionEntity.where { upper(name) == 'A' }.deleteAll()
        session.clear()

        then:
        deleted == 1
        WhereQueryFunctionEntity.list()*.name.sort() == ['b', 'c', 'd']
    }

    void "a function in over a subquery works in a bulk statement"() {
        given:
        seedEntities()
        DetachedCriteria<WhereQueryFunctionEntity> sub = new DetachedCriteria<>(WhereQueryFunctionEntity).build {
            projections { property('name') }
            le('amount', 2)
        }

        when:
        int updated = criteriaOf(new FunctionCallingCriterion('lower', new Query.In('name', sub))).updateAll(amount: 50)
        int deleted = criteriaOf(new FunctionCallingCriterion('upper', new Query.In('name', sub))).deleteAll()
        session.clear()

        then: 'the subquery is evaluated by the database, and the rows it selected are the ones written'
        updated == 2
        deleted == 0
        WhereQueryFunctionEntity.findByName('a').amount == 50
        WhereQueryFunctionEntity.findByName('b').amount == 50
        WhereQueryFunctionEntity.findByName('c').amount == 3
    }

    void "a criterion after a subquery in a bulk statement gets its own parameter"() {
        given:
        seedEntities()
        DetachedCriteria<WhereQueryFunctionEntity> sub = new DetachedCriteria<>(WhereQueryFunctionEntity).build {
            projections { property('name') }
            le('amount', 2)
        }

        when: 'inList then a restriction'
        int inListUpdated = new DetachedCriteria<>(WhereQueryFunctionEntity).build { inList('name', sub); eq('amount', 1) }.updateAll(label: 'in')
        session.clear()

        then:
        inListUpdated == 1
        WhereQueryFunctionEntity.findAllByLabel('in')*.name == ['a']

        when: 'notIn then a restriction'
        int notInUpdated = new DetachedCriteria<>(WhereQueryFunctionEntity).build { notIn('name', sub); eq('amount', 3) }.updateAll(label: 'notin')
        session.clear()

        then:
        notInUpdated == 1
        WhereQueryFunctionEntity.findAllByLabel('notin')*.name == ['c']

        when: 'inList then a not-equal restriction'
        int neUpdated = new DetachedCriteria<>(WhereQueryFunctionEntity).build { inList('name', sub); ne('amount', 1) }.updateAll(label: 'ne')
        session.clear()

        then:
        neUpdated == 1
        WhereQueryFunctionEntity.findAllByLabel('ne')*.name == ['b']
    }

    void "== and != against a projected subquery run"() {
        given:
        seedEntities()

        when:
        def equalToMax = WhereQueryFunctionEntity.where { amount == max(amount) }.list()*.name
        def notMax = WhereQueryFunctionEntity.where { amount != max(amount) }.list()*.name.sort()

        then: 'the row without an amount is not compared, as in SQL'
        equalToMax == ['c']
        notMax == ['a', 'b']
    }

    void "an ordered comparison of a function with a one-row subquery runs: #criterion.simpleName"() {
        given:
        seedEntities()
        new WhereQueryFunctionEntity(name: 'g', label: 'Gamma', amount: 5, created: date(2014, 1, 1)).save(flush: true)
        DetachedCriteria<WhereQueryFunctionEntity> largestAmount = new DetachedCriteria<>(WhereQueryFunctionEntity).build {
            projections { max('amount') }
        }

        when: 'the largest amount is 5, and the label lengths are 5, 4, 5 and 5'
        def names = criteriaOf(new FunctionCallingCriterion('length', criterion.newInstance('label', largestAmount))).list()*.name.sort()

        then: 'the row without a label is not compared, as in SQL'
        names == expected

        where:
        criterion                 || expected
        Query.GreaterThan         || []
        Query.GreaterThanEquals   || ['a', 'd', 'g']
        Query.LessThan            || ['b']
        Query.LessThanEquals      || ['a', 'b', 'd', 'g']
    }

    void "a fractional number compared with a function keeps its precision"() {
        given:
        new WhereQueryFunctionEntity(name: 'abc', amount: 1, created: date(2014, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'abcd', amount: 1, created: date(2014, 1, 1)).save(flush: true)

        when:
        def above = WhereQueryFunctionEntity.where { length(name) > 3.5 }.list()*.name
        def atLeast = WhereQueryFunctionEntity.where { length(name) >= 3.5 }.list()*.name
        def below = WhereQueryFunctionEntity.where { length(name) < 3.5 }.list()*.name.sort()
        def whole = WhereQueryFunctionEntity.where { length(name) == 3.0 }.list()*.name
        def yearAbove = WhereQueryFunctionEntity.where { year(created) > 2013.5 }.list()*.name.sort()

        then:
        above == ['abcd']
        atLeast == ['abcd']
        below == ['abc']
        whole == ['abc']
        yearAbove == ['abc', 'abcd']
    }

    void "a number a hair past a whole number is not the whole number"() {
        given:
        new WhereQueryFunctionEntity(name: 'abc', amount: 1, created: date(2014, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'abcd', amount: 1, created: date(2014, 1, 1)).save(flush: true)

        when:
        def equal = WhereQueryFunctionEntity.where { length(name) == 3.0000000000000000001 }.list()*.name
        def notEqual = WhereQueryFunctionEntity.where { length(name) != 3.0000000000000000001 }.list()*.name.sort()
        def atLeast = WhereQueryFunctionEntity.where { length(name) >= 3.0000000000000000001 }.list()*.name
        def atMost = WhereQueryFunctionEntity.where { length(name) <= 3.0000000000000000001 }.list()*.name

        then:
        equal == []
        notEqual == ['abc', 'abcd']
        atLeast == ['abcd']
        atMost == ['abc']
    }

    void "an in-list of a function can hold a fraction"() {
        given:
        new WhereQueryFunctionEntity(name: 'abc', amount: 1, created: date(2014, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'abcd', amount: 1, created: date(2014, 1, 1)).save(flush: true)

        expect: 'a fraction is never the length of a name, and the whole numbers beside it still match'
        lengthIn([3, 3.5]).list()*.name == ['abc']
        lengthIn([3.5, 4, 3.0000000000000000001]).list()*.name == ['abcd']
        lengthIn([3.5]).list() == []
    }

    void "a bulk statement compares a function with a fraction as it is"() {
        given:
        new WhereQueryFunctionEntity(name: 'abc', amount: 1, created: date(2014, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'abcd', amount: 1, created: date(2014, 1, 1)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'abcde', amount: 1, created: date(2014, 1, 1)).save(flush: true)

        when:
        int above = WhereQueryFunctionEntity.where { length(name) > 3.5 }.updateAll(amount: 2)
        int equal = WhereQueryFunctionEntity.where { length(name) == 3.5 }.updateAll(amount: 3)
        int inList = lengthIn([3, 3.5]).updateAll(amount: 4)
        int deleted = WhereQueryFunctionEntity.where { length(name) < 3.5 }.deleteAll()
        WhereQueryFunctionEntity.withSession { it.clear() }

        then:
        above == 2
        equal == 0
        inList == 1
        deleted == 1
        WhereQueryFunctionEntity.list().collectEntries { [it.name, it.amount] } == [abcd: 2, abcde: 2]
    }

    void "the documented date functions execute on every part of a date"() {
        given:
        new WhereQueryFunctionEntity(name: 'x', amount: 1, created: new GregorianCalendar(2014, 4, 6, 7, 8, 9).time).save(flush: true)
        new WhereQueryFunctionEntity(name: 'y', amount: 1, created: new GregorianCalendar(2015, 5, 7, 8, 9, 10).time).save(flush: true)

        when:
        def byYear = WhereQueryFunctionEntity.where { year(created) == 2014 }.list()*.name
        def byMonth = WhereQueryFunctionEntity.where { month(created) == 5 }.list()*.name
        def byDay = WhereQueryFunctionEntity.where { day(created) == 7 }.list()*.name
        def byHour = WhereQueryFunctionEntity.where { hour(created) == 7 }.list()*.name
        def byMinute = WhereQueryFunctionEntity.where { minute(created) == 9 }.list()*.name
        def bySecond = WhereQueryFunctionEntity.where { second(created) == 9 }.list()*.name

        then:
        byYear == ['x']
        byMonth == ['x']
        byDay == ['y']
        byHour == ['x']
        byMinute == ['y']
        bySecond == ['x']
    }

    void "a function on a property of an association works, as the guide shows: year(pets.birthDate)"() {
        given:
        new WhereQueryFunctionOwner(name: 'old').addToPets(new WhereQueryFunctionPet(birthDate: date(2009, 3, 1)))
                .addToPets(new WhereQueryFunctionPet(birthDate: date(2015, 3, 1))).save(flush: true)
        new WhereQueryFunctionOwner(name: 'new').addToPets(new WhereQueryFunctionPet(birthDate: date(2015, 3, 1))).save(flush: true)

        when:
        def in2009 = WhereQueryFunctionOwner.where { year(pets.birthDate) == 2009 }.list()*.name
        def after2009 = WhereQueryFunctionOwner.where { year(pets.birthDate) > 2009 }.list()*.name.sort()
        def inMarch2015 = WhereQueryFunctionOwner.where { month(pets.birthDate) == 3 && year(pets.birthDate) == 2015 }.list()*.name.sort()

        then:
        in2009 == ['old']
        after2009 == ['new', 'old']
        inMarch2015 == ['new', 'old']
    }

    void "a function name that the where DSL does not support is rejected, and writes nothing"() {
        given:
        seedEntities()
        def criterion = new FunctionCallingCriterion("upper(name) = name OR 1=1 OR upper", new Query.Equals('name', 'x'))

        when:
        criteriaOf(criterion).updateAll(amount: 99)

        then:
        thrown(InvalidDataAccessResourceUsageException)

        when:
        criteriaOf(criterion).list()

        then:
        thrown(InvalidDataAccessResourceUsageException)

        and:
        WhereQueryFunctionEntity.findAllByAmount(99).isEmpty()
    }

    void "a function over an rlike matches on a read and is rejected in a bulk statement, which has no regular expression"() {
        given:
        seedEntities()
        def criterion = new FunctionCallingCriterion('lower', new Query.RLike('name', '^a$'))

        expect: 'the read applies the regular expression'
        criteriaOf(criterion).list()*.name == ['a']

        when: 'a bulk statement would have written it as LIKE and matched differently'
        criteriaOf(criterion).deleteAll()

        then:
        thrown(InvalidDataAccessResourceUsageException)
        WhereQueryFunctionEntity.count() == 4
    }

    void "a function over an equality with a one-row detached criteria deletes through a subquery"() {
        given:
        seedEntities()
        DetachedCriteria<WhereQueryFunctionEntity> oneRow = new DetachedCriteria<>(WhereQueryFunctionEntity).build {
            projections { property('name') }
            eq('amount', 1)
        }

        when:
        int deleted = criteriaOf(new FunctionCallingCriterion('lower', new Query.Equals('name', oneRow))).deleteAll()
        session.clear()

        then:
        deleted == 1
        WhereQueryFunctionEntity.list()*.name.sort() == ['b', 'c', 'd']
    }
}

@Entity
class WhereQueryFunctionOwner implements HibernateEntity<WhereQueryFunctionOwner> {

    String name

    static hasMany = [pets: WhereQueryFunctionPet]
}

@Entity
class WhereQueryFunctionPet implements HibernateEntity<WhereQueryFunctionPet> {

    Date birthDate
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
