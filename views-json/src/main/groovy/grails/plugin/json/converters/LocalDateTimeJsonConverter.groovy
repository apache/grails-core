package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link LocalDateTime} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateTimeJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        LocalDateTime == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((LocalDateTime)value)
    }
}
