package org.grails.datastore.bson.codecs.temporal

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import grails.gorm.time.LocalDateConverter

import java.time.LocalDate

/**
 * A trait to read and write a {@link LocalDate} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalDateBsonConverter implements TemporalBsonConverter<LocalDate>, LocalDateConverter {

    @Override
    void write(BsonWriter writer, LocalDate value) {
        writer.writeDateTime(convert(value))
    }

    @Override
    LocalDate read(BsonReader reader) {
        convert(reader.readDateTime())
    }

    @Override
    BsonType bsonType() {
        BsonType.DATE_TIME
    }
}