package org.grails.forge.feature.database

import groovy.yaml.YamlSlurper
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class HibernateGormSpec extends ApplicationContextSpec implements CommandOutputFixture{

    void "test hibernate gorm features"() {
        when:
        Features features = getFeatures(['gorm-hibernate5'])

        then:
        features.contains("h2")
        features.contains("gorm-hibernate5")
    }

    void "test dependencies are present for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate5"])
                .render()

        then:
        template.contains('implementation "org.grails.plugins:hibernate5"')
        template.contains("runtimeOnly \"org.apache.tomcat:tomcat-jdbc\"")
        template.contains("runtimeOnly \"com.h2database:h2\"")
    }

    void "test dependencies are present for buildSrc"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate5"])
                .renderBuildSrc()

        then:
        template.contains('implementation "org.grails.plugins:hibernate5"')
    }

    void "test buildSrc is present for buildscript dependencies"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final def buildGradle = output["build.gradle"]

        expect:
        buildGradle != null
        buildGradle.contains("classpath \"org.grails.plugins:hibernate5\"")

    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-hibernate5'])

        then:
        ctx.configuration.containsKey("dataSource.pooled")
        ctx.configuration.containsKey("dataSource.jmxExport")
        ctx.configuration.containsKey("hibernate.cache.queries")
        ctx.configuration.containsKey("hibernate.cache.use_second_level_cache")
        ctx.configuration.containsKey("hibernate.cache.use_query_cache")
    }

    void "test match values of datasource config"() {

        when:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String applicationYaml = output["grails-app/conf/application.yml"]
        def config = new YamlSlurper().parseText(applicationYaml)

        then:
        config.environments.development.dataSource.dbCreate == 'create-drop'
        config.environments.test.dataSource.dbCreate == 'update'
        config.environments.production.dataSource.dbCreate == 'none'
        config.environments.development.dataSource.url == 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        config.environments.test.dataSource.url == 'jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        config.environments.production.dataSource.url == 'jdbc:h2:./prodDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'

        config.environments.production.dataSource.properties.jmxEnabled == true
        config.environments.production.dataSource.properties.initialSize == 5
        config.environments.production.dataSource.properties.maxActive == 50
        config.environments.production.dataSource.properties.minIdle == 5
        config.environments.production.dataSource.properties.maxIdle == 25
        config.environments.production.dataSource.properties.maxWait == 10000
        config.environments.production.dataSource.properties.maxAge == 600000
        config.environments.production.dataSource.properties.timeBetweenEvictionRunsMillis == 5000
        config.environments.production.dataSource.properties.minEvictableIdleTimeMillis == 60000
        config.environments.production.dataSource.properties.validationQuery == "SELECT 1"
        config.environments.production.dataSource.properties.validationQueryTimeout == 3
        config.environments.production.dataSource.properties.validationInterval == 15000
        config.environments.production.dataSource.properties.testOnBorrow == true
        config.environments.production.dataSource.properties.testWhileIdle == true
        config.environments.production.dataSource.properties.testOnReturn == false
        config.environments.production.dataSource.properties.jdbcInterceptors == "ConnectionState"
        config.environments.production.dataSource.properties.defaultTransactionIsolation == 2
    }
}
