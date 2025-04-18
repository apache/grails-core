package org.grails.datastore.gorm.neo4j.mapping.config

import grails.neo4j.Direction
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Entity

/**
 * Config for a relationship
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class RelationshipConfig extends NodeConfig {

    /**
     * The default relationship direction
     */
    Direction direction = Direction.OUTGOING

    /**
     * The default relationship type
     */
    String type
}
