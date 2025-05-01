package org.grails.forge.feature.grails

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class GrailsDefaultPluginsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test that default grails plugins are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("implementation \"org.apache.grails:grails-rest-transforms\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-databinding\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-i18n\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-services\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-interceptors\"")
    }

    void "test i18n message properties files are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))

        expect:
        output.containsKey("grails-app/i18n/messages.properties")
        !(Arrays.asList("cs", "da", "de", "es", "fr", "it", "ja", "nb", "nl", "pl", "pt_BR", "pt_PT", "ru", "sk", "sv", "th", "zh_CN")
                .findAll {prop -> { !output.containsKey("grails-app/i18n/messages_" + prop + ".properties") }})
    }

}
