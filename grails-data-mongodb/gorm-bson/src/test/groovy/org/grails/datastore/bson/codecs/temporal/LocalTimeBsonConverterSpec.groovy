package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalTime

class LocalTimeBsonConverterSpec extends Specification implements LocalTimeBsonConverter {

    @Shared
    LocalTime localTime

    void setupSpec() {
        localTime = LocalTime.of(6,5,4,3)
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readInt64() >> 21904000000003
        }

        when:
        LocalTime converted = read(bsonReader)

        then:
        converted.hour == 6
        converted.minute == 5
        converted.second == 4
        converted.nano == 3
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, localTime)

        then:
        1 * bsonWriter.writeInt64(21904000000003)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.INT64
    }
}