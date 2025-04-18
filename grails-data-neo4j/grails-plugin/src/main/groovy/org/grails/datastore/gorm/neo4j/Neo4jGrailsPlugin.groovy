package org.grails.datastore.gorm.neo4j

import grails.core.GrailsClass
import grails.neo4j.bootstrap.Neo4jDataStoreSpringInitializer
import grails.plugins.GrailsPlugin
import grails.plugins.Plugin
import groovy.transform.CompileStatic
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.neo4j.converters.*
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.grails.datastore.gorm.plugin.support.*
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertyResolver

class Neo4jGrailsPlugin extends Plugin {

    def license = "Apache 2.0 License"
    def organization = [ name: "Grails", url: "http://grails.org/" ]
    def developers = [
        [ name: "Graeme Rocher", email: "graeme@grails.org"],
        [ name: "Stefan Armbruster", email: "stefan@armbruster-it.de" ] ]
    def issueManagement = [ system: "JIRA", url: "https://github.com/grails/grails-data-mapping/issues" ]
    def scm = [ url: "https://github.com/grails/grails-data-mapping" ]

    def grailsVersion = "3.3.0 > *"
    def loadAfter = ['domainClass', 'hibernate', 'services', 'converters']
    //def loadBefore = ['dataSource']
    def observe = ['services', 'domainClass']

    def author = "Graeme Rocher"
    def authorEmail = "graeme@grails.org"
    def title = "Neo4j GORM"
    def description = 'A plugin that integrates the Neo4j graph database into Grails, providing a GORM API onto it'

    def documentation = "http://grails.github.io/grails-data-mapping/latest/neo4j"

    def dependsOn = [:]
    // resources that are excluded from plugin packaging

    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    @Override
    @CompileStatic
    Closure doWithSpring() {
        ConfigSupport.prepareConfig(config, (ConfigurableApplicationContext) applicationContext)
        def initializer = new Neo4jDataStoreSpringInitializer((PropertyResolver) config, grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE).collect() { GrailsClass cls -> cls.clazz })
        initializer.registerApplicationIfNotPresent = false
        initializer.setSecondaryDatastore(hasHibernatePlugin())
        return initializer.getBeanDefinitions((BeanDefinitionRegistry)applicationContext)
    }

    @CompileStatic
    protected boolean hasHibernatePlugin() {
        manager.allPlugins.any() { GrailsPlugin plugin -> plugin.name ==~ /hibernate\d*/}
    }

    @Override
    void doWithApplicationContext() {
        Neo4jMappingContext mappingContext = applicationContext.getBean(Neo4jMappingContext)

        mappingContext.addTypeConverter(new LongToLocalDateConverter())
        mappingContext.addTypeConverter(new LongToLocalDateTimeConverter())
        mappingContext.addTypeConverter(new LongToLocalTimeConverter())
        mappingContext.addTypeConverter(new LongToOffsetDateTimeConverter())
        mappingContext.addTypeConverter(new LongToOffsetTimeConverter())
        mappingContext.addTypeConverter(new LongToZonedDateTimeConverter())
        mappingContext.addTypeConverter(new StringToPeriodConverter())
        mappingContext.addTypeConverter(new LongToInstantConverter())

        mappingContext.addTypeConverter(new LocalDateToLongConverter())
        mappingContext.addTypeConverter(new LocalDateTimeToLongConverter())
        mappingContext.addTypeConverter(new LocalTimeToLongConverter())
        mappingContext.addTypeConverter(new OffsetDateTimeToLongConverter())
        mappingContext.addTypeConverter(new OffsetTimeToLongConverter())
        mappingContext.addTypeConverter(new ZonedDateTimeToLongConverter())
        mappingContext.addTypeConverter(new PeriodToStringConverter())
        mappingContext.addTypeConverter(new InstantToLongConverter())
    }
}
