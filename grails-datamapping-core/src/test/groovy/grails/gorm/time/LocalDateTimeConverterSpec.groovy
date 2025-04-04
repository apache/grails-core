package grails.gorm.time

import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LocalDateTimeConverterSpec extends Specification implements LocalDateTimeConverter {

    @Shared
    LocalDateTime localDateTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        LocalDate localDate = LocalDate.of(1941, 1, 5)
        localDateTime = LocalDateTime.of(localDate, localTime)
    }

    void "test convert to long"() {
        expect:
        convert(localDateTime) == -914781296000L
    }

    void "test convert from long"() {
        expect:
        convert(-914781296000L) == localDateTime.withNano(0)
    }

}