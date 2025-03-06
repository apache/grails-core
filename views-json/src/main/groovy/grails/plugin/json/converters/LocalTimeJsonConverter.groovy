package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link LocalTime} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalTimeJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        LocalTime == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_LOCAL_TIME.format((LocalTime)value)
    }
}
