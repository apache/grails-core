package org.grails.datastore.gorm.neo4j

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.mapping.config.NodeConfig
import org.grails.datastore.mapping.model.AbstractClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Represents a mapping between a GORM entity and the Graph
 *
 * @author Stefan Armbruster
 * @author Graeme Rocher
 */
@CompileStatic
class GraphClassMapping extends AbstractClassMapping<NodeConfig> {

    GraphClassMapping(PersistentEntity entity, MappingContext context) {
        super(entity, context)
    }

    @Override
    NodeConfig getMappedForm() {
        ((GraphPersistentEntity)entity).mappedForm
    }
}
