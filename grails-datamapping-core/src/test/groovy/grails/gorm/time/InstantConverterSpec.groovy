package grails.gorm.time

import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

class InstantConverterSpec extends Specification implements InstantConverter {

    @Shared
    Instant instant

    void setupSpec() {
        instant = Instant.ofEpochMilli(100)
    }

    void "test convert to long"() {
        expect:
        convert(instant) == 100L
    }

    void "test convert from long"() {
        expect:
        convert(100L) == instant
    }

}
