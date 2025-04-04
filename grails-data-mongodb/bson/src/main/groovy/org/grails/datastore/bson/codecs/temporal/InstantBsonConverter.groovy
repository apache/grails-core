package org.grails.datastore.bson.codecs.temporal

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import grails.gorm.time.InstantConverter

import java.time.Instant

/**
 * A trait to read and write a {@link java.time.Instant} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait InstantBsonConverter implements TemporalBsonConverter<Instant>, InstantConverter {

    @Override
    void write(BsonWriter writer, Instant value) {
        writer.writeInt64(convert(value))
    }

    @Override
    Instant read(BsonReader reader) {
        convert(reader.readInt64())
    }

    @Override
    BsonType bsonType() {
        BsonType.INT64
    }

}