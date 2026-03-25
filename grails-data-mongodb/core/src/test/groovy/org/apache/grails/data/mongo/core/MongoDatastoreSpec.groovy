package org.apache.grails.data.mongo.core

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Requires

@Requires({ isDockerAvailable() })
abstract class MongoDatastoreSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    static MongoDBContainer mongoDBContainer

    void setupSpec() {
        if (isDockerAvailable()) {
            if (mongoDBContainer == null) {
                mongoDBContainer = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)
                mongoDBContainer.start()
            }
            manager.mongoDBContainer = mongoDBContainer
            manager.configuration = [
                    (MongoSettings.SETTING_DATABASE_NAME): 'test',
                    (MongoSettings.SETTING_HOST)         : mongoDBContainer.host,
                    (MongoSettings.SETTING_PORT)         : mongoDBContainer.getMappedPort(AbstractMongoGrailsExtension.DEFAULT_MONGO_PORT) as String
            ]
        }
    }

    static boolean isDockerAvailable() {
        GrailsDataMongoTckManager.isDockerAvailable()
    }
}