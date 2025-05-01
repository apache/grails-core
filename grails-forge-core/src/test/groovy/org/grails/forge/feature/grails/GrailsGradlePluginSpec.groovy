package org.grails.forge.feature.grails

import org.grails.forge.BeanContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class GrailsGradlePluginSpec extends BeanContextSpec implements CommandOutputFixture {

    void "test build gradle file and gradle properties"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String gradleProps = output["gradle.properties"]

        then:
        gradleProps.contains("grailsVersion=")
    }

    void "test dependencies are present for buildSrc"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .renderBuildSrc()

        then:
        template.contains('implementation "org.apache.grails:grails-gradle-plugins"')
    }

    void "test buildSrc is present for buildscript dependencies"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final def buildGradle = output["build.gradle"]

        expect:
        buildGradle != null
        buildGradle.contains("classpath \"org.apache.grails:grails-gradle-plugins\"")

    }

    void "test dependencies are present for Gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .applicationType(ApplicationType.PLUGIN)
                .render()

        then:
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-plugin\"")
    }

}
