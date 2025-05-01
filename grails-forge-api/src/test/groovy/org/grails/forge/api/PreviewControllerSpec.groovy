package org.grails.forge.api


import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.other.GrailsQuartz
import spock.lang.Specification

@MicronautTest
class PreviewControllerSpec extends Specification {
    @Inject
    PreviewClient client

    void "test default create app command"() {
        when:
        def map = client.previewApp(ApplicationType.DEFAULT_OPTION, "test", Collections.emptyList(), null, null, null)

        then:
        map.contents.containsKey("build.gradle")

    }

    void "test preview - grails-quartz feature"() {
        when:
        def map = client.previewApp(ApplicationType.DEFAULT_OPTION, "test", [GrailsQuartz.FEATURE_NAME], null, null, null)

        then:
        map.contents['build.gradle'].contains('org.apache.grails:grails-quartz')
    }

    void "test preview - bad feature"() {
        when:
        client.previewApp(ApplicationType.DEFAULT_OPTION, "test", ['juikkkk'], null, null, null)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.getResponse().getBody(Map).get()._embedded.errors[0].message == 'The requested feature does not exist: juikkkk'
    }
}
