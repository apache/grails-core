package org.grails.datastore.mapping.mongo

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Constants for use with GORM for MongoDB
 *
 * @since 6.0
 * @author Graeme Rocher
 */
@CompileStatic
class MongoConstants {
    public static final String SET_OPERATOR = '$set';
    public static final String UNSET_OPERATOR = '$unset';
    public static final String CODEC_ENGINE = "codec";
    public static final String MONGO_ID_FIELD = "_id";
    public static final String MONGO_CLASS_FIELD = "_class";
    public static final String INC_OPERATOR = '$inc'
    public static final String ASSIGNED_IDENTIFIER_MAPPING = "assigned"


    @CompileDynamic
    public static <T> T mapToObject(Class<T> targetType, Map<String,Object> values) {
        T t = targetType.getDeclaredConstructor().newInstance()
        for(String name in values.keySet()) {
            if(t.respondsTo(name)) {
                t."$name"( values.get(name) )
            }
        }
        return t
    }
}
