package grails.mongodb.mapping

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.MappingDefinition
import org.grails.datastore.mapping.mongo.config.MongoAttribute
import org.grails.datastore.mapping.mongo.config.MongoCollection

/**
 * Helps to build mapping definitions for Neo4j
 *
 * @since 6.1
 * @author Graeme Rocher
 */
@CompileStatic
class MappingBuilder {

    /**
     * Build a MongoDB document mapping
     *
     * @param mappingDefinition The closure defining the mapping
     * @return The mapping
     */
    static MappingDefinition<MongoCollection, MongoAttribute> document(@DelegatesTo(MongoCollection) Closure mappingDefinition) {
        new ClosureNodeMappingDefinition(mappingDefinition)
    }

    @CompileStatic
    private static class ClosureNodeMappingDefinition implements MappingDefinition<MongoCollection, MongoAttribute> {
        final Closure definition
        private MongoCollection mapping

        ClosureNodeMappingDefinition(Closure definition) {
            this.definition = definition
        }

        @Override
        MongoCollection configure(MongoCollection existing) {
            return MongoCollection.configureExisting(existing, definition)
        }

        @Override
        MongoCollection build() {
            if(mapping == null) {
                MongoCollection nc = new MongoCollection()
                mapping = MongoCollection.configureExisting(nc, definition)
            }
            return mapping
        }

    }
}
