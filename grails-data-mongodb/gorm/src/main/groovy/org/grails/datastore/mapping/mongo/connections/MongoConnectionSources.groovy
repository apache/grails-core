package org.grails.datastore.mapping.mongo.connections

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import groovy.transform.CompileStatic
import org.bson.Document
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.InMemoryConnectionSources
import org.springframework.core.env.PropertyResolver

/**
 *
 * A {@link org.grails.datastore.mapping.core.connections.ConnectionSources} implementation that reads the connections from MongoDB
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class MongoConnectionSources extends InMemoryConnectionSources<MongoClient, MongoConnectionSourceSettings> {
    private static final String CONNECTION_NAME = "name"

    MongoConnectionSources(ConnectionSource<MongoClient, MongoConnectionSourceSettings> defaultConnectionSource, ConnectionSourceFactory<MongoClient, MongoConnectionSourceSettings> connectionSourceFactory, PropertyResolver configuration) {
        super(defaultConnectionSource, connectionSourceFactory, configuration)

        initialize(defaultConnectionSource)
    }

    void initialize(ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource) {
        MongoCollection<Document> mongoCollection = getConnectionsCollection(connectionSource)
        FindIterable findIterable = mongoCollection
                                              .find()

        for(Document d in findIterable) {
            String connectionName = d.getString(CONNECTION_NAME)
            if(connectionName) {
                super.addConnectionSource(connectionName, DatastoreUtils.createPropertyResolver(d))
            }
        }
    }

    @Override
    ConnectionSource<MongoClient, MongoConnectionSourceSettings> addConnectionSource(String name, PropertyResolver configuration) {
        return super.addConnectionSource(name, configuration)
    }

    ConnectionSource<MongoClient, MongoConnectionSourceSettings> addConnectionSource(String name, Document configuration) {
        MongoCollection mongoCollection = getConnectionsCollection(defaultConnectionSource)
        configuration.put(CONNECTION_NAME, name)
        mongoCollection.insertOne(configuration)
        return addConnectionSource(name, DatastoreUtils.createPropertyResolver(configuration))
    }

    ConnectionSource<MongoClient, MongoConnectionSourceSettings> addConnectionSource(String name, Map<String,Object> configuration) {
        return addConnectionSource(name, new Document(configuration))
    }

    protected MongoCollection<Document> getConnectionsCollection(ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource) {
        MongoClient client = connectionSource.getSource()

        MongoConnectionSourceSettings settings = connectionSource.getSettings()
        String collectionName = settings.getConnectionsCollection()
        MongoCollection mongoCollection = client.getDatabase(settings.getDatabase())
                .getCollection(collectionName)
        return mongoCollection
    }
}
