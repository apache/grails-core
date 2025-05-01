package org.grails.forge.feature.test

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GebWithTestcontainersSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test dependencies'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains('integrationTestImplementation testFixtures("org.apache.grails:grails-geb")')
    }

    @Unroll
    void 'test feature geb-with-testcontainers is not supported for #applicationType application'(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(TestFramework.SPOCK), ['geb-with-testcontainers'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: geb-with-testcontainers'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }
}
