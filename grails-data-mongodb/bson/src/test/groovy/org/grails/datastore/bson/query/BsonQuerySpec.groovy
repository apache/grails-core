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

package org.grails.datastore.bson.query

import org.bson.BsonDocument
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.dao.InvalidDataAccessResourceUsageException

import grails.gorm.DetachedCriteria
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.bson.codecs.domain.Address
import org.grails.datastore.bson.codecs.domain.Person
import org.grails.datastore.bson.codecs.domain.Profile
import org.grails.datastore.bson.json.JsonReader
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.query.Query

class BsonQuerySpec extends Specification {

    void "Test parse in query from BSON string"() {
        when:"A bson query is parsed"
        DetachedCriteria criteria = BsonQuery.parse(Person, new JsonReader('{"name":"Fred", "age": { "$in": [18, 25] }}'))
        Query.Conjunction criterion = criteria.criteria[0]
        def criteriaList = criterion.criteria
        then:"It is a conjuction"
        criterion instanceof Query.Conjunction

        and:"The criteria are correct"
        criteriaList[0] instanceof Query.Equals
        criteriaList[0].property == 'name'
        criteriaList[0].value == 'Fred'
        criteriaList[1] instanceof Query.In
        criteriaList[1].property == 'age'
        criteriaList[1].values.contains(18)
        criteriaList[1].values.contains(25)

    }

    void "Test parse a query from a BSON string"() {
        when:"A bson query is parsed"
        DetachedCriteria criteria = BsonQuery.parse(Person, new JsonReader('{"name":"Fred", "age": { "$gt": 18 }}'))
        Query.Conjunction criterion = criteria.criteria[0]
        def criteriaList = criterion.criteria
        then:"It is a conjuction"
        criterion instanceof Query.Conjunction


        and:"The criteria are correct"
        criteriaList[0] instanceof Query.Equals
        criteriaList[0].property == 'name'
        criteriaList[0].value == 'Fred'
        criteriaList[1] instanceof Query.GreaterThan
        criteriaList[1].property == 'age'
        criteriaList[1].value == 18


        when:"A bson or query is parsed"
        criteria = BsonQuery.parse(Person, new JsonReader('{"$or":[{"name":"Fred"}, {"age": { "$gt": 18 }}]}'))
        Query.Disjunction disjunction = criteria.criteria[0]
        criteriaList = disjunction.criteria

        then:"The criteria are correct"
        criteriaList[0] instanceof Query.Equals
        criteriaList[0].property == 'name'
        criteriaList[0].value == 'Fred'
        criteriaList[1] instanceof Query.GreaterThan
        criteriaList[1].property == 'age'
        criteriaList[1].value == 18

    }

    void "Test create a bson query from criteria"() {

        when:"A BSON query is created"

        def context = new KeyValueMappingContext("test")
        def entity = context.addPersistentEntity(Person)
        def codecRegistry = new TestCodecRegistry(context)
        def criteria = new DetachedCriteria(Person)
        criteria = criteria.build {
            idEq(1)
            eq('name', 'Fred')
            gt('age', 18)
        }
        Document query = BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then:"The document is correct"
        query != null
        query.get("id") == 1
        query.get('name') == "Fred"
        query.get('age') == [(BsonQuery.GT_OPERATOR):18]

    }

    @Unroll
    void 'Test #label criterion rejects a Map value carrying an operator key'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test')
            def entity = context.addPersistentEntity(Person)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'A criterion value is a map that carries an operator key'
            def criteria = new DetachedCriteria(Person).build(queryClosure)
            BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The query is refused instead of being sent as an operator expression'
            def e = thrown(InvalidDataAccessResourceUsageException)
            e.message.contains('.Person]')
            !e.message.contains('$ne')

        where:
            label          | queryClosure
            'eq'           | { eq('name', ['$ne': 'x']) }
            'ne'           | { ne('name', ['$ne': 'x']) }
            'gt'           | { gt('age', ['$ne': 'x']) }
            'ge'           | { ge('age', ['$ne': 'x']) }
            'lt'           | { lt('age', ['$ne': 'x']) }
            'le'           | { le('age', ['$ne': 'x']) }
            'between from' | { between('age', ['$ne': 'x'], 100) }
            'between to'   | { between('age', 0, ['$ne': 'x']) }
            'inList'       | { inList('age', [18, ['$ne': 'x']]) }
            'eq on id'     | { eq('id', ['$ne': 'x']) }
            'idEq'         | { idEq(['$ne': 'x']) }
            'negated eq'   | { not { eq('name', ['$ne': 'x']) } }
            'nested or'    | { or { eq('name', 'Fred'); eq('name', ['$ne': 'x']) } }
    }

    void 'Test a bson Document value is rejected like any other Map'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test')
            def entity = context.addPersistentEntity(Person)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'A criterion value is a driver Document carrying an operator'
            def criteria = new DetachedCriteria(Person).build {
                eq('name', new Document('$regex', '^a'))
            }
            BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The query is refused'
            thrown(InvalidDataAccessResourceUsageException)
    }

    void 'Test a Map value on a Map typed property builds a subdocument match'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test').tap {
                addPersistentEntity(Address)
            }
            def entity = context.addPersistentEntity(Profile)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'A Map typed property is compared to a plain map'
            def criteria = new DetachedCriteria(Profile).build {
                eq('attributes', [color: 'red', nested: [size: 'large']])
            }
            def query = BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The map is used as a literal subdocument'
            query.get('attributes') == [color: 'red', nested: [size: 'large']]
    }

    @Unroll
    void 'Test a Map typed property rejects operator key #value'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test').tap {
                addPersistentEntity(Address)
            }
            def entity = context.addPersistentEntity(Profile)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'A Map typed property is compared to a map that carries an operator key at any depth'
            def criteria = new DetachedCriteria(Profile).build {
                eq('attributes', value)
            }
            BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The query is refused'
            def e = thrown(InvalidDataAccessResourceUsageException)
            e.message.contains('[attributes]')

        where:
            value << [
                    ['$exists': true],
                    [color: ['$ne': 'red']],
                    [tags: [['$gt': 1]]],
                    [tags: [['$gt': 1]] as Object[]],
                    [("${'$'}exists"): true],
                    new Document('$where', 'sleep(1000)')
            ]
    }

    void 'Test a Map value without operator keys is sent as a literal on any property'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test').tap {
                addPersistentEntity(Address)
            }
            def entity = context.addPersistentEntity(Profile)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'Maps without operator keys are compared to a String property, a dotted path and the identity'
            def criteria = new DetachedCriteria(Profile).build {
                eq('name', [first: 'Fred'])
                eq('attributes.nested', [size: 'large'])
                ne('id', [a: 1])
            }
            Document query = BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'Each map is used as a literal subdocument comparison'
            query.get('name') == [first: 'Fred']
            query.get('attributes.nested') == [size: 'large']
            query.get('id') == [(BsonQuery.NE_OPERATOR): [a: 1]]
    }

    @Unroll
    void 'Test a self referential #label value is rejected instead of overflowing the stack'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test').tap {
                addPersistentEntity(Address)
            }
            def entity = context.addPersistentEntity(Profile)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'A Map typed property is compared to a value that contains itself'
            def criteria = new DetachedCriteria(Profile).build {
                eq('attributes', value)
            }
            BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The query is refused with the standard exception'
            def e = thrown(InvalidDataAccessResourceUsageException)
            e.message.contains('nested deeper than')

        where:
            label   | value
            'Map'   | cyclicMap()
            'List'  | [tags: cyclicList()]
            'array' | [tags: cyclicArray()]
    }

    private static Map cyclicMap() {
        def map = [color: 'red']
        map.self = map
        map
    }

    private static List cyclicList() {
        def list = []
        list << list
        list
    }

    private static Object[] cyclicArray() {
        Object[] array = new Object[1]
        array[0] = array
        array
    }

    void 'Test embedded property equality is still encoded through the codec'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext('test').tap {
                addPersistentEntity(Address)
            }
            def entity = context.addPersistentEntity(Profile)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'An embedded property is compared to an instance'
            def criteria = new DetachedCriteria(Profile).build {
                eq('address', new Address(city: 'Stockholm'))
            }
            def query = BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The instance is encoded as a document'
            query.get('address') instanceof BsonDocument
            ((BsonDocument) query.get('address')).getString('city').value == 'Stockholm'
    }

    void 'Test scalar criterion values are still passed through unchanged'() {
        given: 'A mapping context'
            def context = new KeyValueMappingContext("test")
            def entity = context.addPersistentEntity(Person)
            def codecRegistry = new TestCodecRegistry(context)

        when: 'Ordinary values are used'
            def criteria = new DetachedCriteria(Person).build {
                ne('name', 'Fred')
                between('age', 18, 65)
                inList('pattern', ['a', 'b'])
            }
            def query = BsonQuery.createBsonQuery(codecRegistry, entity, criteria.criteria)

        then: 'The document is correct'
            query.get('name') == [(BsonQuery.NE_OPERATOR): 'Fred']
            query.get('age') == [(BsonQuery.GTE_OPERATOR): 18, (BsonQuery.LTE_OPERATOR): 65]
            query.get('pattern') == [(BsonQuery.IN_OPERATOR): ['a', 'b']]
    }

    static class TestCodecRegistry implements CodecRegistry {
        final MappingContext mappingContext

        TestCodecRegistry(MappingContext mappingContext) {
            this.mappingContext = mappingContext
        }

        @Override
        def <T> Codec<T> get(Class<T> clazz) {
            return new BsonPersistentEntityCodec(this, mappingContext.getPersistentEntity(clazz.name))
        }

        @Override
        def <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
            return get(clazz)
        }
    }
}
