package org.grails.datastore.gorm.neo4j.collection

import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.schemaless.DynamicAttributes

/**
 * Default implementation of the {@link Relationship} trait
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class Neo4jRelationship<F, T> implements Relationship<F, T> {
    String type

    Neo4jRelationship(F from, T to, String type) {
        this.from = from
        this.to = to
        this.type = type
    }

    Neo4jRelationship(F from, T to, org.neo4j.driver.types.Relationship neoRel) {
        this.from = from
        this.to = to
        this.type = neoRel.type()
        this.id = neoRel.id()
    }
    /**
     * Allows accessing to dynamic properties with the dot operator
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    def propertyMissing(String name) {
        return getAt(name)
    }

    /**
     * Allows setting a dynamic property via the dot operator
     * @param instance The instance
     * @param name The property name
     * @param val The value
     */
    def propertyMissing(String name, val) {
        putAt(name, val)
    }
}
