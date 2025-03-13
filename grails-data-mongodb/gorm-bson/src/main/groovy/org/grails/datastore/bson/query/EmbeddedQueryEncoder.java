package org.grails.datastore.bson.query;

import org.grails.datastore.mapping.model.types.Embedded;

/**
 * Encodes an embedded object as a query
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface EmbeddedQueryEncoder {

    /**
     * Takes an embedded property and instance and returns the query encoded value
     *
     * @param embedded The embedded association
     * @param instance The instance
     * @return The encoded value
     */
    Object encode(Embedded embedded, Object instance);
}
