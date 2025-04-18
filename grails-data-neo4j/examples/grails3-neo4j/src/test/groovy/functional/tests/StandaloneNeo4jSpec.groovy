package functional.tests


import org.grails.datastore.gorm.neo4j.Neo4jGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class StandaloneNeo4jSpec extends Specification implements GrailsUnitTest {

    @Override
    Set<String> getIncludePlugins() {
        ["domainClass"]
    }

    void "test that both grailsDomainClassMappingContext and neo4jMappingContext are same when using standalone Neo4j"() {

        setup:
        Neo4jGrailsPlugin plugin = new Neo4jGrailsPlugin()
        plugin.grailsApplication = grailsApplication
        plugin.applicationContext = applicationContext
        plugin.setPluginManager(new MockGrailsPluginManager(grailsApplication))
        this.defineBeans(plugin)

        expect:
        applicationContext.containsBean("grailsDomainClassMappingContext")
        applicationContext.containsBean("neo4jMappingContext")
        applicationContext.getBean("grailsDomainClassMappingContext") == applicationContext.getBean("neo4jMappingContext")
    }
}
