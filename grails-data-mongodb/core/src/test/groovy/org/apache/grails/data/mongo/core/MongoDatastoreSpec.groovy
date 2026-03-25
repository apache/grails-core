package org.apache.grails.data.mongo.core

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Requires

@Requires({ isDockerAvailable() })
abstract class MongoDatastoreSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    static boolean isDockerAvailable() {
        GrailsDataMongoTckManager.isDockerAvailable()
    }
}