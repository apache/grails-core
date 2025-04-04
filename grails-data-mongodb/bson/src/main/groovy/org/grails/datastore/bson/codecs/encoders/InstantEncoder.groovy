package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.bson.codecs.temporal.InstantBsonConverter

import java.time.Instant

import static org.grails.datastore.bson.codecs.encoders.SimpleEncoder.TypeEncoder

/**
 * A simple encoder for {@link java.time.Instant}
 *
 * @author James Kleeh
 */
@CompileStatic
class InstantEncoder implements TypeEncoder, InstantBsonConverter {

    @Override
    void encode(BsonWriter writer, PersistentProperty property, Object value) {
        write(writer, (Instant)value)
    }
}