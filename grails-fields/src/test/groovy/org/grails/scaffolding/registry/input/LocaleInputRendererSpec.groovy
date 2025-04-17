package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

@Subject(LocaleInputRenderer)
class LocaleInputRendererSpec extends Specification {

    @Shared
    LocaleInputRenderer renderer

    void setup() {
        renderer = new LocaleInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> Locale
        }

        then:
        renderer.supports(property)
    }

    void "test option key and value"() {
        given:
        Locale locale

        when:
        locale = Locale.US

        then:
        renderer.getOptionKey(locale) == "en_US"
        renderer.getOptionValue(locale) == "en, US,  English (United States)"

        when:
        locale = Locale.ENGLISH

        then:
        renderer.getOptionKey(locale) == "en"
        renderer.getOptionValue(locale) == "en, English"
    }
}
