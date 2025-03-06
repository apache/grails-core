package functional.tests

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic
import org.springframework.core.Ordered

/**
 * Created by jameskleeh on 10/25/16.
 */
@CompileStatic
class MyConverter implements JsonGenerator.Converter, Ordered {

    @Override
    boolean handles(Class<?> type) {
        CustomClass.isAssignableFrom(type)
    }

    @Override
    Object convert(Object value, String key) {
        ((CustomClass)value).name
    }

    @Override
    int getOrder() {
        return -2
    }
}
