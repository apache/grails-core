/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

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
