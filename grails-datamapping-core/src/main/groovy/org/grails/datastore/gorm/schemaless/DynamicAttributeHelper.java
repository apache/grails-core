package org.grails.datastore.gorm.schemaless;

/**
 * Helper class for use in other traits
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class DynamicAttributeHelper {

    public static void setAttribute(DynamicAttributes dynamicAttributes, String name, Object value) {
        dynamicAttributes.putAt(name, value);
    }
}
