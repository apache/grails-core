package org.apache.grails.data.mongo.core

import org.testcontainers.spock.Testcontainers
import spock.lang.Shared

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Requires

@Testcontainers
@Requires({ isDockerAvailable() })
abstract class MongoDatastoreSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    @Shared MongoDBContainer container = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)

    void setupSpec() {
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.mongoDBContainer = container
        manager.configuration = [
                (MongoSettings.SETTING_DATABASE_NAME): 'test',
                (MongoSettings.SETTING_HOST)         : container.host,
                (MongoSettings.SETTING_PORT)         : container.getMappedPort(AbstractMongoGrailsExtension.DEFAULT_MONGO_PORT) as String
        ]
    }

    void cleanupSpec() {
        // We let Testcontainers handle shutdown via its Ryuk container, or we could stop it here 
        // if we wanted to recreate it per-spec. Keeping it static across the suite is faster.
    }

    static boolean isDockerAvailable() {
        GrailsDataMongoTckManager.isDockerAvailable()
    }
}