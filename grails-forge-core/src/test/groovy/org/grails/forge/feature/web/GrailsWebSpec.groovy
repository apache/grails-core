package org.grails.forge.feature.web

import groovy.yaml.YamlSlurper
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class GrailsWebSpec extends ApplicationContextSpec implements CommandOutputFixture{

    void "test grails-web feature"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final def buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
    }

    void "test grails-web configuration"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String applicationYaml = output["grails-app/conf/application.yml"]
        def config = new YamlSlurper().parseText(applicationYaml)

        then:
        config.grails.views.default.codec == 'html'
    }
}
