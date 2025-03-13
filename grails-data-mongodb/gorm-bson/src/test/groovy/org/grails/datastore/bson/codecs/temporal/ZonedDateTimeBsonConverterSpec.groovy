package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.*

class ZonedDateTimeBsonConverterSpec extends Specification implements ZonedDateTimeBsonConverter {

    @Shared
    ZonedDateTime zonedDateTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        LocalDate localDate = LocalDate.of(1941, 1, 5)
        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime)
        zonedDateTime = ZonedDateTime.of(localDateTime, ZoneOffset.ofHours(-6))
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readDateTime() >> -914759696000
        }

        when:
        ZonedDateTime converted = read(bsonReader)

        then:
        converted.hour == 5 || converted.hour == 4 //Converted to system default offset. Changes depending on DST
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
        write(bsonWriter, zonedDateTime)

        then:
        1 * bsonWriter.writeDateTime(-914759696000)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.DATE_TIME
    }
}
