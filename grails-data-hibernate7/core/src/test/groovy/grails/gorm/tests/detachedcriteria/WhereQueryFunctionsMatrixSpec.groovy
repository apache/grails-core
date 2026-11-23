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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import spock.lang.Unroll

/**
 * Every function of a where query, against every kind of comparison and every kind of operand, on a read and in a
 * bulk statement. The read is the reference: a bulk statement has to change exactly the rows the read returns, or fail
 * where the read fails. A cell of the grid that differs is a bug in the bulk path, whichever input it is.
 */
class WhereQueryFunctionsMatrixSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(WhereQueryFunctionEntity)
    }

    private static final List<String> TEXT_FUNCTIONS = ['lower', 'upper', 'trim']
    private static final List<String> NUMBER_FUNCTIONS = ['length', 'year', 'month', 'day', 'hour', 'minute', 'second']

    private static Date at(int year, int month, int day, int hour, int minute, int second) {
        new GregorianCalendar(year, month - 1, day, hour, minute, second).time
    }

    private void seed() {
        new WhereQueryFunctionEntity(name: 'a', amount: 1, created: at(2011, 3, 4, 5, 6, 7)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'b', amount: 2, created: at(2012, 11, 24, 15, 16, 17)).save(flush: true)
        new WhereQueryFunctionEntity(name: '3.5', amount: 3, created: null).save(flush: true)
        new WhereQueryFunctionEntity(name: 'AB', amount: null, created: at(2013, 1, 2, 0, 0, 0)).save(flush: true)
        new WhereQueryFunctionEntity(name: ' pad ', amount: 5, created: at(2011, 12, 31, 23, 59, 59)).save(flush: true)
    }

    private static DetachedCriteria<WhereQueryFunctionEntity> subquery(boolean projected, boolean text) {
        new DetachedCriteria<>(WhereQueryFunctionEntity).build {
            if (projected) {
                projections {
                    if (text) {
                        property('name')
                    } else {
                        max('amount')
                    }
                }
            }
            if (text) {
                eq('name', 'a')
            } else {
                eq('amount', 2)
            }
        }
    }

    private static Query.PropertyCriterion comparison(String kind, String property, Object operand) {
        switch (kind) {
            case 'eq': return new Query.Equals(property, operand)
            case 'ne': return new Query.NotEquals(property, operand)
            case 'gt': return new Query.GreaterThan(property, operand)
            case 'ge': return new Query.GreaterThanEquals(property, operand)
            case 'lt': return new Query.LessThan(property, operand)
            case 'le': return new Query.LessThanEquals(property, operand)
            case 'like': return new Query.Like(property, operand as String)
            case 'ilike': return new Query.ILike(property, operand as String)
            case 'in': return new Query.In(property, operand as List)
            case 'inSub': return new Query.In(property, operand as DetachedCriteria)
        }
        throw new IllegalArgumentException(kind)
    }

    private static List<Map> cases() {
        List<Map> cases = []
        def add = { String fn, String kind, String label, Object operand ->
            cases << [fn: fn, kind: kind, label: label, operand: operand]
        }
        TEXT_FUNCTIONS.each { fn ->
            ['eq', 'ne', 'gt', 'ge', 'lt', 'le'].each { kind ->
                add(fn, kind, 'text', 'a')
                add(fn, kind, 'GString', "${'a'}")
                add(fn, kind, 'fraction', 3.5d)
                add(fn, kind, 'whole number', 7)
                add(fn, kind, 'null', null)
                add(fn, kind, 'subquery with projection', subquery(true, true))
                add(fn, kind, 'subquery without projection', subquery(false, true))
            }
            ['like', 'ilike'].each { kind -> add(fn, kind, 'pattern', 'a%') }
            add(fn, 'in', 'values', ['a', 'B', '3.5'])
            add(fn, 'in', 'one value', ['a'])
            add(fn, 'in', 'empty list', [])
            add(fn, 'in', 'fraction in list', ['a', 3.5d])
            add(fn, 'inSub', 'subquery with projection', subquery(true, true))
            add(fn, 'inSub', 'subquery without projection', subquery(false, true))
        }
        NUMBER_FUNCTIONS.each { fn ->
            ['eq', 'ne', 'gt', 'ge', 'lt', 'le'].each { kind ->
                add(fn, kind, 'int', 1)
                add(fn, kind, 'long', 1L)
                add(fn, kind, 'whole big decimal', 1.0G)
                add(fn, kind, 'fraction', 3.5d)
                add(fn, kind, 'big decimal fraction', 2.5G)
                add(fn, kind, 'text number', '1')
                add(fn, kind, 'GString', "${1}")
                add(fn, kind, 'null', null)
                add(fn, kind, 'subquery with projection', subquery(true, false))
                add(fn, kind, 'subquery without projection', subquery(false, false))
            }
            add(fn, 'in', 'values', [1, 2, 3])
            add(fn, 'in', 'one value', [1])
            add(fn, 'in', 'empty list', [])
            add(fn, 'in', 'fraction in list', [1, 3.5d])
            add(fn, 'inSub', 'subquery with projection', subquery(true, false))
            add(fn, 'inSub', 'subquery without projection', subquery(false, false))
        }
        cases
    }


    void 'the where DSL: an empty list and a subquery without projection behave the same on a read and in a bulk statement'() {
        given:
        seed()
        def named = WhereQueryFunctionEntity.where { name == 'a' }

        when: 'an empty list'
        def emptyRead = WhereQueryFunctionEntity.where { upper(name) in [] }.list()
        def emptyUpdated = WhereQueryFunctionEntity.where { upper(name) in [] }.updateAll(label: 'x')
        def emptyDeleted = WhereQueryFunctionEntity.where { upper(name) in [] }.deleteAll()

        then: 'it matches nothing, whatever the statement'
        emptyRead.isEmpty()
        emptyUpdated == 0
        emptyDeleted == 0

        when: 'a subquery without projection'
        def subRead = WhereQueryFunctionEntity.where { lower(name) == named }.list()*.name
        def subUpdated = WhereQueryFunctionEntity.where { lower(name) == named }.updateAll(label: 'x')
        def subDeleted = WhereQueryFunctionEntity.where { lower(name) == named }.deleteAll()

        then: 'it is compared by the property, as on a read'
        subRead == ['a']
        subUpdated == 1
        subDeleted == 1
    }

    @Unroll
    void '#fn #kind with #label: bulk update and delete change what the read returns'() {
        given:
        seed()
        String property = fn in TEXT_FUNCTIONS || fn == 'length' ? 'name' : 'created'
        def criterion = new FunctionCallingCriterion(fn, comparison(kind, property, operand))
        DetachedCriteria<WhereQueryFunctionEntity> criteria = new DetachedCriteria<>(WhereQueryFunctionEntity)
        criteria.add(criterion)
        Set<Long> all = WhereQueryFunctionEntity.list()*.id as Set

        when: 'the read'
        def read
        try {
            read = criteria.list()*.id as Set
        } catch (Exception e) {
            read = "FAILS ${e.class.simpleName}".toString()
        }

        and: 'the bulk update'
        def update
        try {
            int updated = criteria.updateAll(label: 'hit')
            session.clear()
            update = WhereQueryFunctionEntity.findAllByLabel('hit')*.id as Set
            assert updated == update.size()
        } catch (Exception e) {
            update = "FAILS ${e.class.simpleName}".toString()
        }

        and: 'the bulk delete'
        def bulk
        try {
            int deleted = criteria.deleteAll()
            session.clear()
            bulk = (all - (WhereQueryFunctionEntity.list()*.id as Set)) as Set
            assert deleted == bulk.size()
        } catch (Exception e) {
            bulk = "FAILS ${e.class.simpleName}".toString()
        }

        then: 'both change the same rows, or both fail'
        read instanceof String ? (bulk instanceof String && update instanceof String) : (bulk == read && update == read)

        where:
        [fn, kind, label, operand] << cases().collect { [it.fn, it.kind, it.label, it.operand] }
    }
}
