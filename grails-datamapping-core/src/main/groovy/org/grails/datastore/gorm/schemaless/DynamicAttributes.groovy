package org.grails.datastore.gorm.schemaless

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable

/**
 * A trait that adds support for defining dynamic attributes for databases that support it
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
trait DynamicAttributes {

    private transient Map<String, Object> dynamicAttributes = [:]

    private void putAtDynamic(String name, value) {
        def oldValue = dynamicAttributes.put(name, value)
        if(oldValue != value) {
            if(this instanceof DirtyCheckable) {
                ((DirtyCheckable)this).markDirty(name, value, oldValue)
            }
        }
    }

    /**
     * Sets a dynamic attribute
     *
     * @param name The name of the attribute
     * @param value The value of the attribute
     */
    void putAt(String name, value) {
        if(this.hasProperty(name)) {
            try {
                ((GroovyObject)this).setProperty(name, value)
            } catch (ReadOnlyPropertyException e) {
                putAtDynamic(name, value)
            }
        } else {
            putAtDynamic(name, value)
        }
    }

    /**
     * Obtains a dynamic attribute
     *
     * @param name The name of the attribute
     * @return The value of the attribute
     */
    def getAt(String name) {
        if(this.hasProperty(name)) {
            return ((GroovyObject)this).getProperty(name)
        }
        else {
            dynamicAttributes.get(name)
        }
    }

    /**
     * Obtain the dynamic attributes
     *
     * @return The dynamic attributes
     */
    Map<String, Object> attributes() {
        return this.dynamicAttributes
    }

    /**
     * Obtain the dynamic attributes combined with the provided attributes
     *
     * @param newAttributes The new attributes
     * @return The dynamic attributes
     */
    Map<String, Object> attributes(Map<String, Object> newAttributes) {
        if(newAttributes != null) {
            this.dynamicAttributes.putAll(newAttributes)
        }
        return dynamicAttributes
    }
}