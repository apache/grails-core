package org.grails.forge.feature.database

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class MongoGormSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test Mongo gorm features"() {
        when:
        Features features = getFeatures(['gorm-mongodb'])

        then:
        features.contains("gorm-mongodb")
    }

    void "test Mongo gorm with Embedded MongoDB features "() {
        when:
        Features features = getFeatures(['gorm-mongodb', 'embedded-mongodb'])

        then:
        features.contains("gorm-mongodb")
        features.contains("embedded-mongodb")
    }

    void "test there can only be one of either MongoDB or Neo4j feature"() {
        when:
        getFeatures(beanContext.getBeansOfType(GormOneOfFeature)*.name)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("There can only be one of the following features selected")
    }

    void "test dependencies are present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(["gorm-mongodb"])
                .render()

        then:
        template.contains("implementation \"org.apache.grails:grails-data-mongodb\"")
    }

    void "test gorm mongodb with embedded-mongodb feature"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(["gorm-mongodb", "embedded-mongodb"])
                .render()

        then:
        template.contains("implementation \"org.apache.grails:grails-data-mongodb\"")
        template.contains("testRuntimeOnly \"org.grails.plugins:embedded-mongodb:2.0.1\"")
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-mongodb'])

        then:
        ctx.configuration.containsKey("grails.mongodb.url")
    }


}
