package org.grails.datastore.bson.codecs.temporal

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import grails.gorm.time.OffsetTimeConverter

import java.time.OffsetTime

/**
 * A trait to read and write a {@link OffsetTime} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait OffsetTimeBsonConverter implements TemporalBsonConverter<OffsetTime>, OffsetTimeConverter {

    @Override
    void write(BsonWriter writer, OffsetTime value) {
        writer.writeInt64(convert(value))
    }

    @Override
    OffsetTime read(BsonReader reader) {
        convert(reader.readInt64())
    }

    @Override
    BsonType bsonType() {
        BsonType.INT64
    }
}