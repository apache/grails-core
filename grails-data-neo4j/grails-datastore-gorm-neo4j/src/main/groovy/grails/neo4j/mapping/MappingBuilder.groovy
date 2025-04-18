package grails.neo4j.mapping

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.mapping.config.Attribute
import org.grails.datastore.gorm.neo4j.mapping.config.NodeConfig
import org.grails.datastore.gorm.neo4j.mapping.config.RelationshipConfig
import org.grails.datastore.mapping.config.MappingDefinition

/**
 * Helps to build mapping definitions for Neo4j
 *
 * @since 6.1
 * @author Graeme Rocher
 */
@CompileStatic
class MappingBuilder {

    /**
     * Build a Neo4j node mapping
     *
     * @param mappingDefinition The closure defining the mapping
     * @return The mapping
     */
    static MappingDefinition<NodeConfig, Attribute> node(@DelegatesTo(NodeConfig) Closure mappingDefinition) {
        new ClosureNodeMappingDefinition(mappingDefinition)
    }

    /**
     * Build a Neo4j relationship mapping
     *
     * @param mappingDefinition The closure defining the mapping
     * @return The mapping
     */
    static MappingDefinition<RelationshipConfig, Attribute> relationship(@DelegatesTo(RelationshipConfig) Closure mappingDefinition) {
        new ClosureRelMappingDefinition(mappingDefinition)
    }


    @CompileStatic
    private static class ClosureRelMappingDefinition implements MappingDefinition<RelationshipConfig, Attribute> {
        final Closure definition
        private RelationshipConfig mapping

        ClosureRelMappingDefinition(Closure definition) {
            this.definition = definition
        }

        @Override
        RelationshipConfig configure(RelationshipConfig existing) {
            RelationshipConfig.configureExisting(existing, definition)
        }

        @Override
        RelationshipConfig build() {
            if(mapping == null) {
                RelationshipConfig nc = new RelationshipConfig()
                mapping = RelationshipConfig.configureExisting(nc, definition)
            }
            return mapping
        }

    }
    @CompileStatic
    private static class ClosureNodeMappingDefinition implements MappingDefinition<NodeConfig, Attribute> {
        final Closure definition
        private NodeConfig mapping

        ClosureNodeMappingDefinition(Closure definition) {
            this.definition = definition
        }

        @Override
        NodeConfig configure(NodeConfig existing) {
            NodeConfig.configureExisting(existing, definition)
        }

        @Override
        NodeConfig build() {
            if(mapping == null) {
                NodeConfig nc = new NodeConfig()
                mapping = NodeConfig.configureExisting(nc, definition)
            }
            return mapping
        }

    }
}
