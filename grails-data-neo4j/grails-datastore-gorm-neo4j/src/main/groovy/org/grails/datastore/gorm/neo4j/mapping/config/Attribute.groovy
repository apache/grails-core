package org.grails.datastore.gorm.neo4j.mapping.config

import grails.neo4j.Direction
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Property

/**
 * Represents a Neo4j attribute or relationships config
 *
 * @author Graeme Rocher
 * @since 6.0.5
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Attribute extends Property {

    /**
     * The relationship direction
     */
    Direction direction
    /**
     * Sets the relationship type
     *
     * @param relationshipType The relationship type
     */
    void setType(String relationshipType) {
        setTargetName(relationshipType)
    }

    /**
     * @return The relationship type
     */
    String getType() {
        getTargetName()
    }

    /**
     * Sets the relationship type
     *
     * @param relationshipType The relationship type
     */
    Attribute type(String relationshipType) {
        setType(relationshipType)
        return this
    }

}
