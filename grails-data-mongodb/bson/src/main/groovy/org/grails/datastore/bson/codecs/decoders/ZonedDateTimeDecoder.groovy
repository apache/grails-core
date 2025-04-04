package org.grails.datastore.bson.codecs.decoders

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.bson.codecs.temporal.ZonedDateTimeBsonConverter

import static org.grails.datastore.bson.codecs.decoders.SimpleDecoder.TypeDecoder

/**
 * A simple decoder for {@link java.time.ZonedDateTime}
 *
 * @author James Kleeh
 */
@CompileStatic
class ZonedDateTimeDecoder implements TypeDecoder, ZonedDateTimeBsonConverter {

    @Override
    void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
        entityAccess.setPropertyNoConversion(property.name, read(reader))
    }
}
