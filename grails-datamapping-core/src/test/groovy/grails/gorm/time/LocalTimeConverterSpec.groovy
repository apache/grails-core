package grails.gorm.time

import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalTime

class LocalTimeConverterSpec extends Specification implements LocalTimeConverter {

    @Shared
    LocalTime localTime

    void setupSpec() {
        localTime = LocalTime.of(6,5,4,3)
    }

    void "test convert to long"() {
        expect:
        convert(localTime) == 21904000000003L
    }

    void "test convert from long"() {
        expect:
        convert(21904000000003L) == localTime
    }

}