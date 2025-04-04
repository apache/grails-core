package grails.mongodb.api

import com.mongodb.ReadPreference
import com.mongodb.client.AggregateIterable
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.FindOneAndDeleteOptions
import grails.gorm.api.GormStaticOperations
import org.bson.Document
import org.bson.conversions.Bson
import org.grails.datastore.gorm.mongo.MongoCriteriaBuilder

import java.util.function.Function

/**
 * Static operations for GORM for MongoDB
 *
 * @author Graeme rocher
 * @since 6.0
 */
interface MongoStaticOperations<D> extends GormStaticOperations<D> {

    /**
     * Find an entity for the given filter
     *
     * @param filter The filter
     *
     * @return The FindIterable
     */
    FindIterable<D> find(Bson filter)

    /**
     * Atomically find a document and remove it.
     *
     * @param filter the query filter to find the document with
     * @return the document that was removed.  If no documents matched the query filter, then null will be returned
     */
    D findOneAndDelete(Bson filter)

    /**
     * Atomically find a document and remove it.
     *
     * @param filter the query filter to find the document with
     * @return the document that was removed.  If no documents matched the query filter, then null will be returned
     */
    D findOneAndDelete(Bson filter, FindOneAndDeleteOptions options)

    /**
     * @return Custom MongoDB criteria builder
     */
    MongoCriteriaBuilder createCriteria()

    /**
     * @return The database for this domain class
     */
    MongoDatabase getDB()

    /**
     * @return The name of the Mongo collection that entity maps to
     */
    String getCollectionName()

    /**
     * The actual collection that this entity maps to.
     *
     * @return The actual collection
     */
    MongoCollection<Document> getCollection()

    /**
     * Use the given collection for this entity for the scope of the closure call
     * @param collectionName The collection name
     * @param callable The callable
     * @return The result of the closure
     */
    public <T> T withCollection(String collectionName, Closure<T> callable)

    /**
     * Use the given collection for this entity for the scope of the session
     *
     * @param collectionName The collection name
     * @return The previous collection name
     */
    String useCollection(String collectionName)

    /**
     * Use the given database for this entity for the scope of the closure call
     * @param databaseName The collection name
     * @param callable The callable
     * @return The result of the closure
     */
    public <T> T withDatabase(String databaseName, Closure<T> callable)

    /**
     * Use the given database for this entity for the scope of the session
     *
     * @param databaseName The collection name
     * @return The previous database name
     */
    String useDatabase(String databaseName)

    /**
     * Counts the number of hits
     * @param query The query
     * @return The hit count
     */
    int countHits(String query)

    /**
     * Execute a MongoDB aggregation pipeline. Note that the pipeline should return documents that represent this domain class as each return document will be converted to a domain instance in the result set
     *
     * @param pipeline The pipeline
     * @return A mongodb result list
     */
    List<D> aggregate(List pipeline)

    /**
     * Execute a MongoDB aggregation pipeline. Note that the pipeline should return documents that represent this domain class as each return document will be converted to a domain instance in the result set
     *
     * @param pipeline The pipeline
     * @param doWithAggregate A callback to modify the aggregate iterable
     * @return A mongodb result list
     */
    List<D> aggregate(List pipeline, Function<AggregateIterable, AggregateIterable> doWithAggregate)

    /**
     * Execute a MongoDB aggregation pipeline. Note that the pipeline should return documents that represent this domain class as each return document will be converted to a domain instance in the result set
     *
     * @param pipeline The pipeline
     * @param doWithAggregate A callback to modify the aggregate iterable
     * @return A mongodb result list
     */
    List<D> aggregate(List pipeline, Function<AggregateIterable, AggregateIterable> doWithAggregate, ReadPreference readPreference)
    /**
     * Search for entities using the given query
     *
     * @param query The query
     * @return The results
     */
    List<D> search(String query)
    /**
     * Search for entities using the given query
     *
     * @param query The query
     * @return The results
     */
    List<D> search(String query, Map options)
    /**
     * Searches for the top results ordered by the MongoDB score
     *
     * @param query The query
     * @return The results
     */
    List<D> searchTop(String query)

    /**
     * Searches for the top results ordered by the MongoDB score
     *
     * @param query The query
     * @param limit The maximum number of results. Defaults to 5.
     * @return The results
     */
    List<D> searchTop(String query, int limit)

    /**
     * Searches for the top results ordered by the MongoDB score
     *
     * @param query The query
     * @param limit The maximum number of results. Defaults to 5.
     * @return The results
     */
    List<D> searchTop(String query, int limit, Map options )
}