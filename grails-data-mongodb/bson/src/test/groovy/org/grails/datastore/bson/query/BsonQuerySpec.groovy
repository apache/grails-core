package org.grails.datastore.bson.query

import grails.gorm.DetachedCriteria
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.bson.codecs.domain.Person
import org.grails.datastore.bson.json.JsonReader
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

/**
 * Created by graemerocher on 22/06/16.
 */
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
