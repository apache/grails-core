package org.grails.forge.feature.database

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class MongoSyncSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test readme.md with feature mongo-sync contains links to micronaut and 3rd party docs'() {
        when:
        def output = generate(['mongo-sync'])
        def readme = output["README.md"]

        then:
        readme
        readme.contains("https://docs.mongodb.com/drivers/java/sync/current/")
        readme.contains("https://docs.mongodb.com")
    }

    void "test mongo sync features"() {
        when:
        Features features = getFeatures(['mongo-sync'])

        then:
        features.contains("mongo-sync")
    }

    void "test mongo sync dependencies are present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(["mongo-sync"])
                .render()

        then:
        template.contains('implementation "org.mongodb:mongodb-driver-sync"')
        template.contains('testImplementation "org.testcontainers:mongodb"')
    }
}
