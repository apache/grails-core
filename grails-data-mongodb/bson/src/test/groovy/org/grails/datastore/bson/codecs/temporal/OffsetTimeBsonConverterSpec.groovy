package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

class OffsetTimeBsonConverterSpec extends Specification implements OffsetTimeBsonConverter {

    @Shared
    OffsetTime offsetTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        offsetTime = OffsetTime.of(localTime, ZoneOffset.ofHours(-6))
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readInt64() >> 43504000000003
        }

        when:
        OffsetTime converted = read(bsonReader)

        then:
        converted.hour == 5 || converted.hour == 4 //Converted to system default offset. Changes depending on DST
        converted.minute == 5
        converted.second == 4
        converted.nano == 3
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, offsetTime)

        then:
        1 * bsonWriter.writeInt64(43504000000003)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.INT64
    }
}