package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.Period

/**
 * A class to render a {@link Period} as json
 *
 * @author Muhammad Hamza Zaib
 */
@CompileStatic
class PeriodJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        Period == type
    }

    @Override
    Object convert(Object value, String key) {
        ((Period)value).toString()
    }
}
