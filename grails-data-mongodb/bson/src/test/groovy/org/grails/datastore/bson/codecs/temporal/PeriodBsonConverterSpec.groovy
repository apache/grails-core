package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.Period

class PeriodBsonConverterSpec extends Specification implements PeriodBsonConverter {

    @Shared
    Period period

    void setupSpec() {
        period = Period.of(1941, 1, 5)
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readString() >> 'P1941Y1M5D'
        }

        when:
        Period converted = read(bsonReader)

        then:
        converted.years == 1941
        converted.months == 1
        converted.days == 5
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, period)

        then:
        1 * bsonWriter.writeString('P1941Y1M5D')
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.STRING
    }
}