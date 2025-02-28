package demo

import grails.testing.web.taglib.TagLibUnitTest
import grails.validation.Validateable
import org.springframework.context.i18n.LocaleContextHolder
import spock.lang.Specification

class LocaleTagLibSpec extends Specification implements TagLibUnitTest<SampleTagLib> {

    void 'test customizing messageSource'() {
        given:
        def w = new Widget()
        LocaleContextHolder.setLocale(Locale.US)
        messageSource.addMessage("demo.Widget.title.label", Locale.US, "Title Of Widget")
        messageSource.addMessage("demo.Widget.label", Locale.US, "Widget")

        when:
        w.validate()

        then:
        w.hasErrors()

        when:
        def template = '<g:renderErrors bean="${widget}" />'

        then:
        applyTemplate(template, [widget: w]).contains("<li>Property [Title Of Widget] of class [Widget] cannot be null</li>")
    }
}


class Widget implements Validateable {
    String title
}
