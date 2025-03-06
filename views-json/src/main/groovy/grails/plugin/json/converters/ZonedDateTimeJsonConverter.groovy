package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link ZonedDateTime} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class ZonedDateTimeJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        ZonedDateTime == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_ZONED_DATE_TIME.format((ZonedDateTime)value)
    }
}
