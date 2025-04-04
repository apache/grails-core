package grails.gorm.time

import spock.lang.Shared
import spock.lang.Specification

import java.time.*

class ZonedDateTimeConverterSpec extends Specification implements ZonedDateTimeConverter {

    @Shared
    ZonedDateTime zonedDateTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        LocalDate localDate = LocalDate.of(1941, 1, 5)
        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime)
        zonedDateTime = ZonedDateTime.of(localDateTime, ZoneOffset.ofHours(-6))
    }

    void "test convert to long"() {
        expect:
        convert(zonedDateTime) == -914759696000L
    }

    void "test convert from long"() {
        given:
        ZonedDateTime converted = convert(-914759696000L)

        expect:
        converted == zonedDateTime.withNano(0).withZoneSameInstant(ZoneOffset.ofHours(-7)) || converted == zonedDateTime.withNano(0).withZoneSameInstant(ZoneOffset.ofHours(-8))
    }

}