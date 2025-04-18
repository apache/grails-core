package functional.tests


import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.grails.datastore.gorm.neo4j.Neo4jGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class Neo4jWithHibernateSpec extends Specification implements GrailsUnitTest {

    @Override
    Set<String> getIncludePlugins() {
        ["domainClass"]
    }

    void "test that both grailsDomainClassMappingContext and neo4jMappingContext are same when using Neo4j with Hibernate"() {

        setup:
        GrailsPluginManager pluginManager = new MockGrailsPluginManager(grailsApplication)
        GrailsPlugin hibernate = Mock(GrailsPlugin)
        hibernate.getName() >> "hibernate"
        pluginManager.registerMockPlugin(hibernate)

        Neo4jGrailsPlugin neo4jGrailsPlugin = new Neo4jGrailsPlugin()
        neo4jGrailsPlugin.grailsApplication = grailsApplication
        neo4jGrailsPlugin.applicationContext = applicationContext
        neo4jGrailsPlugin.pluginManager = pluginManager
        this.defineBeans(neo4jGrailsPlugin)

        expect:
        applicationContext.containsBean("grailsDomainClassMappingContext")
        applicationContext.containsBean("neo4jMappingContext")
        applicationContext.getBean("grailsDomainClassMappingContext") != applicationContext.getBean("neo4jMappingContext")
    }
}
