package org.grails.datastore.bson.codecs

import groovy.json.JsonSlurper
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.grails.datastore.bson.json.JsonReader
import org.grails.datastore.bson.json.JsonWriter
import org.grails.datastore.bson.codecs.domain.Person
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.core.convert.converter.Converter
import spock.lang.Specification

import java.text.SimpleDateFormat

/**
 * Created by graemerocher on 14/06/16.
 */
class BsonPersistentEntityCodecSpec extends Specification {

    void "test marshall entity to JSON"() {
        given:"A mapping context"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        PersistentEntity entity = mappingContext.addPersistentEntity(Person)

        CodecRegistry codecRegistry = CodecRegistries.fromProviders(new CodecExtensions())

        when:"An entity is is marshaled"

        BsonPersistentEntityCodec codec = new BsonPersistentEntityCodec(codecRegistry, entity)

        def sw = new StringWriter()
        def format = new SimpleDateFormat('yyyy/MM/dd')
        TimeZone UTC = TimeZone.getTimeZone("UTC");
        format.setTimeZone(UTC)

        def date = format.parse('1973/07/09')
        codec.encode(
                new JsonWriter(
                        sw,
                        JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()
                ),
                new Person(name: 'Fred', age: 12, dateOfBirth: date)
        )

        def json = new JsonSlurper().parseText(sw.toString())
        then:"The result is encoded JSON"
        json.age == 12
        json.dateOfBirth == "1973-07-09T00:00+0000"
        json.name == "Fred"

    }

    void "Test read entity from JSON"() {
        given:"A mapping context"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        def format = new SimpleDateFormat(JsonWriter.ISO_8601)
        TimeZone UTC = TimeZone.getTimeZone("UTC");
        format.setTimeZone(UTC)
        mappingContext.converterRegistry.addConverter(new Converter<String, Date>() {
            @Override
            Date convert(String source) {

                return format.parse(source)
            }
        })
        PersistentEntity entity = mappingContext.addPersistentEntity(Person)

        CodecRegistry codecRegistry = CodecRegistries.fromProviders(new CodecExtensions())

        when:"An entity is is marshaled"

        BsonPersistentEntityCodec codec = new BsonPersistentEntityCodec(codecRegistry, entity)

        Person person = codec.decode(new JsonReader('{"age":12,"dateOfBirth":"1973-07-09T00:00+0000","name":"Fred"}'))


        then:"The result is encoded JSON"
        person != null
        person.name == "Fred"
        format.format(person.dateOfBirth) == "1973-07-09T00:00+0000"
        person.age == 12

    }

    void "Test read entity from JSON with null values"() {
        given:"A mapping context"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        def format = new SimpleDateFormat(JsonWriter.ISO_8601)
        TimeZone UTC = TimeZone.getTimeZone("UTC");
        format.setTimeZone(UTC)
        mappingContext.converterRegistry.addConverter(new Converter<String, Date>() {
            @Override
            Date convert(String source) {

                return format.parse(source)
            }
        })
        PersistentEntity entity = mappingContext.addPersistentEntity(Person)

        CodecRegistry codecRegistry = CodecRegistries.fromProviders(new CodecExtensions())

        when:"An entity is is marshaled"

        BsonPersistentEntityCodec codec = new BsonPersistentEntityCodec(codecRegistry, entity)

        Person person = codec.decode(new JsonReader('{"age":12,"dateOfBirth":null,"name":"Fred"}'))


        then:"The result is encoded JSON"
        person != null
        person.name == "Fred"
        person.dateOfBirth == null
        person.age == 12

    }


    void "Test read entity from JSON with regex values"() {
        given:"A mapping context"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        def format = new SimpleDateFormat(JsonWriter.ISO_8601)
        TimeZone UTC = TimeZone.getTimeZone("UTC");
        format.setTimeZone(UTC)
        mappingContext.converterRegistry.addConverter(new Converter<String, Date>() {
            @Override
            Date convert(String source) {

                return format.parse(source)
            }
        })
        PersistentEntity entity = mappingContext.addPersistentEntity(Person)

        CodecRegistry codecRegistry = CodecRegistries.fromProviders(new CodecExtensions())

        when:"An entity is is marshaled"

        BsonPersistentEntityCodec codec = new BsonPersistentEntityCodec(codecRegistry, entity)

        Person person = codec.decode(new JsonReader('{"age":12,"dateOfBirth":null,"name":"Fred", pattern:/\\sfoo+/}'))


        then:"The result is encoded JSON"
        person != null
        person.name == "Fred"
        person.dateOfBirth == null
        person.age == 12
        person.pattern == /\sfoo+/
    }
}


