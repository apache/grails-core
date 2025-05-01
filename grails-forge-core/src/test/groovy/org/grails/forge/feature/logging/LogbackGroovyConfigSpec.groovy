package org.grails.forge.feature.logging

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.*
import spock.lang.Unroll

class LogbackGroovyConfigSpec extends ApplicationContextSpec implements CommandOutputFixture {

    @Unroll
    void "test grails-logging dependency is present for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(TestFramework.SPOCK), ['logbackGroovy'])

        then:
        output.containsKey("build.gradle")
        def build = output.get("build.gradle")
        build.contains("implementation \"org.apache.grails:grails-logging\"")
        build.contains("implementation \"io.github.virtualdogbert:logback-groovy-config:1.12.4\"")

        where:
        applicationType << ApplicationType.values().toList()
    }

    @Unroll
    void "test logback.xml config file is present for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(TestFramework.SPOCK), ['logbackGroovy'])

        then:
        output.containsKey("grails-app/conf/logback-config.groovy")

        where:
        applicationType << ApplicationType.values().toList()
    }
}
