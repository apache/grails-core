package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link OffsetDateTime} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class OffsetDateTimeJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        OffsetDateTime == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((OffsetDateTime)value)
    }
}
