package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month

class LocalDateTimeBsonConverterSpec extends Specification implements LocalDateTimeBsonConverter {

    @Shared
    LocalDateTime localDateTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        LocalDate localDate = LocalDate.of(1941, 1, 5)
        localDateTime = LocalDateTime.of(localDate, localTime)
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readDateTime() >> -914781296000
        }

        when:
        LocalDateTime converted = read(bsonReader)

        then:
        converted.hour == 6
        converted.minute == 5
        converted.second == 4
        converted.nano == 0 //Nanoseconds is lost
        converted.year == 1941
        converted.month == Month.JANUARY
        converted.dayOfMonth == 5
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, localDateTime)

        then:
        1 * bsonWriter.writeDateTime(-914781296000)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.DATE_TIME
    }
}