package grails.mongodb.api

import grails.gorm.api.GormInstanceOperations
import org.bson.Document

/**
 * Instance methods for GORM for MongoDB
 * @author Graeme Rocher
 * @since 6.0
 */
interface MongoInstanceOperations<D> extends GormInstanceOperations<D> {

    /**
     * Return the DBObject instance for the entity
     *
     * @deprecated use dynamic properties instead
     * @param instance The instance
     * @return The DBObject instance
     */
    @Deprecated
    Document getDbo(D instance)
}