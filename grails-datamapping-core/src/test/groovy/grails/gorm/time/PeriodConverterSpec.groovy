package grails.gorm.time

import spock.lang.Shared
import spock.lang.Specification

import java.time.Period

class PeriodConverterSpec extends Specification implements PeriodConverter {

    @Shared
    Period period

    void setupSpec() {
        period = Period.of(1941, 1, 5)
    }

    void "test convert to long"() {
        expect:
        convert(period) == 'P1941Y1M5D'
    }

    void "test convert from long"() {
        expect:
        convert('P1941Y1M5D') == period
    }

}
