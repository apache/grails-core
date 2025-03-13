package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

class InstantBsonConverterSpec extends Specification implements InstantBsonConverter {

    @Shared
    Instant instant

    void setupSpec() {
        instant = Instant.ofEpochMilli(100)
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readInt64() >> 100L
        }

        when:
        Instant converted = read(bsonReader)

        then:
        converted.toEpochMilli() == 100L
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, instant)

        then:
        1 * bsonWriter.writeInt64(100L)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.INT64
    }
}