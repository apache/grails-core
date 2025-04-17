package grails.views.resolve

import spock.lang.Specification

/**
 * Created by graemerocher on 19/05/16.
 */
class TemplateResolveUtilsSpec extends Specification {

    void "Test template name calculations"() {
        expect:
        TemplateResolverUtils.shortTemplateNameForClass(Object) == '/object/_object'
        TemplateResolverUtils.fullTemplateNameForClass(Object) == '/java/lang/_object'
    }
}
