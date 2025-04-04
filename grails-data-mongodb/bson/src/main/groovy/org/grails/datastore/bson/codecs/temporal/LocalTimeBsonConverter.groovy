package org.grails.datastore.bson.codecs.temporal

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import grails.gorm.time.LocalTimeConverter

import java.time.LocalTime

/**
 * A trait to read and write a {@link LocalTime} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalTimeBsonConverter implements TemporalBsonConverter<LocalTime>, LocalTimeConverter {

    @Override
    void write(BsonWriter writer, LocalTime value) {
        writer.writeInt64(convert(value))
    }

    @Override
    LocalTime read(BsonReader reader) {
        convert(reader.readInt64())
    }

    @Override
    BsonType bsonType() {
        BsonType.INT64
    }
}