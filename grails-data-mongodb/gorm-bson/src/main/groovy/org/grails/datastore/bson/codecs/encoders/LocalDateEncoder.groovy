package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.bson.codecs.temporal.LocalDateBsonConverter

import java.time.LocalDate

import static org.grails.datastore.bson.codecs.encoders.SimpleEncoder.TypeEncoder

/**
 * A simple encoder for {@link LocalDate}
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateEncoder implements TypeEncoder, LocalDateBsonConverter {

    @Override
    void encode(BsonWriter writer, PersistentProperty property, Object value) {
        write(writer, (LocalDate)value)
    }
}
