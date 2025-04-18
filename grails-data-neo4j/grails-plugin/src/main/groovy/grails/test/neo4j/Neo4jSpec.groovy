package grails.test.neo4j

import grails.config.Config
import groovy.transform.CompileStatic
import org.grails.config.PropertySourcesConfig
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.transactions.Transaction
import org.springframework.boot.env.PropertySourceLoader
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.PropertySource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.SpringFactoriesLoader
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Base class for Neo4j tests
 *
 * @author Graeme Rocher
 * @since 6.0.1
 */
@CompileStatic
abstract class Neo4jSpec extends Specification {

    @Shared
    @AutoCleanup
    Neo4jDatastore neo4jDatastore

    @Shared
    Neo4jSession session

    @Shared
    Transaction transaction

    /**
     * @return Obtains the mapping context
     */
    MappingContext getMappingContext() {
        neo4jDatastore.getMappingContext()
    }


    /**
     * @return The domain classes
     */
    protected List<Class> getDomainClasses() { [] }

    void setupSpec() {
        List<PropertySourceLoader> propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class, getClass().getClassLoader())
        ResourceLoader resourceLoader = new DefaultResourceLoader()

        List<PropertySource> propertySources = []
        PropertySourceLoader ymlLoader = propertySourceLoaders.find { it.getFileExtensions().toList().contains("yml") }
        if (ymlLoader) {
            propertySources.addAll(load(resourceLoader, ymlLoader, "application.yml"))
        }
        PropertySourceLoader groovyLoader = propertySourceLoaders.find { it.getFileExtensions().toList().contains("groovy") }
        if (groovyLoader) {
            propertySources.addAll(load(resourceLoader, groovyLoader, "application.groovy"))
        }

        if(propertySources) {
            Map<String, Object> mapPropertySource = [:] as Map<String, Object>
            mapPropertySource += propertySources
                    .findAll { it.getSource() }
                    .collectEntries { it.getSource() as Map }

            Config config = new PropertySourcesConfig(mapPropertySource)

            List<Class> domainClasses = getDomainClasses()
            if (!domainClasses) {
                def packageToScan = getPackageToScan(config)
                neo4jDatastore = new Neo4jDatastore((PropertyResolver) config, packageToScan)
            }
            else {
                neo4jDatastore = new Neo4jDatastore((PropertyResolver) config, (domainClasses as Class[]))
            }
        }
        else {
            List<Class> domainClasses = getDomainClasses()
            if (!domainClasses) {
                neo4jDatastore = new Neo4jDatastore(getClass().getPackage())
            }
            else {
                neo4jDatastore = new Neo4jDatastore(domainClasses as Class[])
            }
        }
    }

    void setup() {
        boolean existing = neo4jDatastore.hasCurrentSession()
        session = (Neo4jSession)( existing ? neo4jDatastore.currentSession : DatastoreUtils.bindSession(neo4jDatastore.connect()) )
        transaction = session.beginTransaction()
    }

    void cleanup() {
        transaction.rollback()
        if (!neo4jDatastore.hasCurrentSession()) {
            TransactionSynchronizationManager.unbindResource(neo4jDatastore)
            DatastoreUtils.closeSessionOrRegisterDeferredClose(session, neo4jDatastore)
        }
    }

    /**
     * Obtains the default package to scan
     *
     * @param config The configuration
     * @return The package to scan
     */
    protected Package getPackageToScan(Config config) {
        def p = Package.getPackage(config.getProperty('grails.codegen.defaultPackage', getClass().package.name))
        if(p == null) {
            p = getClass().package
        }
        return p
    }

    private List<PropertySource> load(ResourceLoader resourceLoader, PropertySourceLoader loader, String filename) {
        if (canLoadFileExtension(loader, filename)) {
            Resource appYml = resourceLoader.getResource(filename)
            return loader.load(appYml.getDescription(), appYml) as List<PropertySource>
        } else {
            return Collections.emptyList()
        }
    }

    private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
        return Arrays
                .stream(loader.fileExtensions)
                .map { String extension -> extension.toLowerCase() }
                .anyMatch { String extension -> name.toLowerCase().endsWith(extension) }
    }
}
