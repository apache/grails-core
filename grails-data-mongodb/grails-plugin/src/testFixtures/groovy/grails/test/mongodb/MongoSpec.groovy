package grails.test.mongodb

import com.mongodb.client.MongoClient
import grails.config.Config
import groovy.transform.CompileStatic
import org.grails.config.PropertySourcesConfig
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.mongo.MongoDatastore
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
 * Base class for MongoDB tests
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 6.0.1
 */
@CompileStatic
abstract class MongoSpec extends Specification {

    @Shared
    @AutoCleanup
    MongoDatastore mongoDatastore

    @Shared
    Session mongoSession

    /**
     * @return Obtains the mapping context
     */
    MappingContext getMappingContext() {
        mongoDatastore.getMappingContext()
    }

    /**
     * @return The default mongo client
     */
    MongoClient createMongoClient() {
        return null
    }

    /**
     * @return The domain classes
     */
    protected List<Class> getDomainClasses() { [] }

    void setupSpec() {
        List<PropertySourceLoader> propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class, getClass().getClassLoader());
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

        Map<String, Object> mapPropertySource = propertySources
            .findAll { it.getSource() }
            .collectEntries { it.getSource() as Map }

        Config config = new PropertySourcesConfig(mapPropertySource)

        List<Class> domainClasses = getDomainClasses()
        if (!domainClasses) {
            def packageToScan = getPackageToScan(config)
            MongoClient mongoClient = createMongoClient()
            def pkg = getClass().classLoader.getDefinedPackage(packageToScan)
            if (pkg == null) {
                throw new ConfigurationException("Package to scan [$packageToScan] cannot be found on the classpath")
            }

            if (mongoClient) {
                mongoDatastore = new MongoDatastore(mongoClient, config, pkg)
            } else {
                mongoDatastore = new MongoDatastore((PropertyResolver) config, pkg)
            }
        }
        else {
            MongoClient mongoClient = createMongoClient()
            if (mongoClient) {
                mongoDatastore = new MongoDatastore(mongoClient, config, domainClasses as Class[])
            } else {
                mongoDatastore = new MongoDatastore((PropertyResolver) config, domainClasses as Class[])
            }
        }
    }

    void setup() {
        boolean existing = mongoDatastore.hasCurrentSession()
        mongoSession = existing ? mongoDatastore.currentSession : DatastoreUtils.bindSession(mongoDatastore.connect())
    }

    void cleanup() {
        if (!mongoDatastore.hasCurrentSession()) {
            TransactionSynchronizationManager.unbindResource(mongoDatastore)
            DatastoreUtils.closeSessionOrRegisterDeferredClose(mongoSession, mongoDatastore)
        }
    }

    /**
     * @return Obtain the mongo client
     */
    MongoClient getMongoClient() {
        mongoDatastore.getConnectionSources().defaultConnectionSource.source
    }

    /**
     * Obtains the default package to scan
     *
     * @param config The configuration
     * @return The package to scan
     */
    protected String getPackageToScan(Config config) {
        config.getProperty('grails.codegen.defaultPackage', getClass().package.name)
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
