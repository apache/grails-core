package org.grails.forge.feature.grailsProfiles

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GrailsProfilesSpec extends ApplicationContextSpec implements CommandOutputFixture {

    @Unroll
    void "test profile dependency is present for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(TestFramework.SPOCK))

        then:
        output.containsKey("build.gradle")
        def build = output.get("build.gradle")
        build.contains("profile \"org.apache.grails.profiles:${applicationType.name.replace("_", "-")}\"")

        where:
        applicationType << ApplicationType.values().toList()
    }

    void "test config"() {
        when:
        def output = generate(applicationType, new Options(TestFramework.SPOCK))

        then:
        output.containsKey("grails-app/conf/application.yml")
        def build = output.get("grails-app/conf/application.yml")
        build.contains("defaultPackage")
        build.contains("profile")

        where:
        applicationType << ApplicationType.values().toList()
    }
}
