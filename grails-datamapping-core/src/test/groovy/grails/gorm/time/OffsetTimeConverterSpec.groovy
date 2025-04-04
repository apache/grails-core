package grails.gorm.time

import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

class OffsetTimeConverterSpec extends Specification implements OffsetTimeConverter {

    @Shared
    OffsetTime offsetTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        offsetTime = OffsetTime.of(localTime, ZoneOffset.ofHours(-6))
    }

    void "test convert to long"() {
        expect:
        convert(offsetTime) == 43504000000003L
    }

    void "test convert from long"() {
        given:
        OffsetTime converted = convert(43504000000003L)

        expect:
        converted == offsetTime.withOffsetSameInstant(ZoneOffset.ofHours(-7)) ||
                converted == offsetTime.withOffsetSameInstant(ZoneOffset.ofHours(-8))
    }

}
