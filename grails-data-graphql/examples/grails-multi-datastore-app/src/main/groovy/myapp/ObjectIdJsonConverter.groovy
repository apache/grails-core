package myapp

import grails.plugin.json.builder.JsonGenerator
import org.bson.types.ObjectId

class ObjectIdJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        ObjectId.isAssignableFrom(type)
    }

    @Override
    Object convert(Object value, String key) {
        ((ObjectId) value).toString()
    }
}
