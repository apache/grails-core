package org.grails.datastore.bson.codecs.temporal

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter

/**
 * A trait to read and write a {@link java.time.temporal.Temporal} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait TemporalBsonConverter<T> {

    abstract void write(BsonWriter writer, T value)

    abstract T read(BsonReader reader)

    abstract BsonType bsonType()
}