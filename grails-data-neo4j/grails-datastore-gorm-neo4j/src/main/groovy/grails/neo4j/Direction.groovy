package grails.neo4j

import groovy.transform.CompileStatic

/**
 * Used to configure the direction of a relationship
 *
 * @author Graeme Rocher
 * @since 6.0.5
 */
@CompileStatic
enum Direction {
    INCOMING, OUTGOING, BOTH

    @Override
    String toString() {
        switch (this) {
            case INCOMING: return '<-'
            case OUTGOING: return '->'
            case BOTH: return '<->'
        }
    }

    /**
     * @return Whether the direction is incoming
     */
    boolean isIncoming() {
        return this == INCOMING || this == BOTH
    }

    /**
     * @return Whether the direction is outgoing
     */
    boolean isOutgoing() {
        return this == OUTGOING || this == BOTH
    }
}