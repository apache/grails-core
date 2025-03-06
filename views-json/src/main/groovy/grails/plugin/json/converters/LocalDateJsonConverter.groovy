package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link LocalDate} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        LocalDate == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_LOCAL_DATE.format((LocalDate)value)
    }
}
