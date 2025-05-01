package org.grails.forge.feature.view

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class ScaffoldingSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test scaffolding feature"() {
        when:
        final Features features = getFeatures(['scaffolding'])

        then:
        features.contains('scaffolding')
    }

    void "test dependencies are present in build.gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(['scaffolding'])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-scaffolding"')
        template.contains('runtimeOnly "org.fusesource.jansi:jansi"')
    }

}
