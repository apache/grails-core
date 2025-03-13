package functional.tests

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import groovy.transform.CompileStatic
import org.testcontainers.containers.MongoDBContainer

@CompileStatic
trait EmbeddedMongoClient {

    abstract MongoDBContainer getMongoDBContainer()

    MongoClient createMongoClient() {
        if (!mongoDBContainer.isRunning()) {
            mongoDBContainer.start()
        }
        return MongoClients.create(mongoDBContainer.getReplicaSetUrl())
    }
}
