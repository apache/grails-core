package org.grails.forge.feature.grails

import org.grails.forge.BeanContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features

class GrailsWebConsoleSpec extends BeanContextSpec {

    void "test grails-web-console feature"() {
        when:
        Features features = getFeatures(["grails-web-console"])

        then:
        features.contains("grails-web-console")
    }

    void "test dependency is present for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["grails-web-console"])
                .render()

        then:
        template.contains("runtimeOnly \"org.grails.plugins:grails-console\"")
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext([ "grails-web-console"])

        then:
        ctx.configuration.containsKey("environments.production.grails.plugin.console.enabled")
        ctx.configuration.containsKey("environments.production.grails.plugin.console.fileStore.remote.enabled")
    }
}
