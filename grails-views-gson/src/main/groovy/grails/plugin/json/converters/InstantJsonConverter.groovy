package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.Instant

/**
 * A class to render a {@link java.time.Instant} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class InstantJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        Instant == type
    }

    @Override
    Object convert(Object value, String key) {
        ((Instant)value).toEpochMilli()
    }
}