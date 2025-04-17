package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.OffsetTime
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link OffsetTime} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class OffsetTimeJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        OffsetTime == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_OFFSET_TIME.format((OffsetTime)value)
    }
}
