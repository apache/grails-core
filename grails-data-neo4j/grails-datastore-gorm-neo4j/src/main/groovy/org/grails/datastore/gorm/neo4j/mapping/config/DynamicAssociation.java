package org.grails.datastore.gorm.neo4j.mapping.config;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.PersistentProperty;

/**
 * Represents a dynamic associations in Neo4j
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public interface DynamicAssociation<T extends Property> extends PersistentProperty<T> {
}
