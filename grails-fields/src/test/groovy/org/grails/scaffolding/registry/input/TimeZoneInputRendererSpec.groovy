package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

@Subject(TimeZoneInputRenderer)
class TimeZoneInputRendererSpec extends Specification {

    @Shared
    TimeZoneInputRenderer renderer

    void setup() {
        renderer = new TimeZoneInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> TimeZone
        }

        then:
        renderer.supports(property)
    }

    void "test option key and value"() {
        given:
        TimeZone timeZone = TimeZone.getTimeZone("America/New_York")

        expect:
        renderer.getOptionKey(timeZone) == "America/New_York"
//        renderer.getOptionValue(timeZone) == "EDT, Eastern Daylight Time -5:0.0 [America/New_York]"
    }
}
