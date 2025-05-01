package org.grails.forge.feature.test

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GebWithWebDriverBinariesSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test dependencies'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK), ['geb-with-webdriver-binaries'])
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains('integrationTestImplementation testFixtures("org.apache.grails:grails-geb")')
        buildGradle.contains('testImplementation "org.seleniumhq.selenium:selenium-api"')
        buildGradle.contains('testImplementation "org.seleniumhq.selenium:selenium-support"')
        buildGradle.contains('testImplementation "org.seleniumhq.selenium:selenium-remote-driver"')
        buildGradle.contains('testRuntimeOnly "org.seleniumhq.selenium:selenium-firefox-driver"')
    }

    void 'test GebConfig.groovy file is present'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK), ['geb-with-webdriver-binaries'])

        expect:
        output.containsKey('src/integration-test/resources/GebConfig.groovy')
    }

    @Unroll
    void 'test feature geb is not supported for #applicationType application'(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(TestFramework.SPOCK), ['geb-with-webdriver-binaries'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: geb-with-webdriver-binaries'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }

    void 'test webdriver binaries gradle configurations'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK), ['geb-with-webdriver-binaries'])
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains('id "com.github.erdi.webdriver-binaries"')
        buildGradle.contains('webdriverBinaries')
        buildGradle.contains("chromedriver '122.0.6260.0'")
        buildGradle.contains("geckodriver '0.33.0'")
        buildGradle.contains("edgedriver '110.0.1587.57'")
    }

    void 'test webdriver binaries gradle configurations for windows OS'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK, OperatingSystem.WINDOWS), ['geb-with-webdriver-binaries'])
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains('id "com.github.erdi.webdriver-binaries"')
        buildGradle.contains('webdriverBinaries')
        buildGradle.contains("chromedriver '122.0.6260.0'")
        buildGradle.contains("geckodriver '0.33.0'")
        buildGradle.contains("edgedriver '110.0.1587.57'")
    }
}
