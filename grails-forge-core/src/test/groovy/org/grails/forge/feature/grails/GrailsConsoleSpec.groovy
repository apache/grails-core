package org.grails.forge.feature.grails

import org.grails.forge.BeanContextSpec
import org.grails.forge.BuildBuilder

class GrailsConsoleSpec extends BeanContextSpec {

    void "test dependency"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["grails-console"])
                .render()

        then:
        template.contains("console \"org.apache.grails:grails-console\"")
    }
}
