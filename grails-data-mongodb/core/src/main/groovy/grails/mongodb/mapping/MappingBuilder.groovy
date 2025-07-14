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
            if (mapping == null) {
                MongoCollection nc = new MongoCollection()
                mapping = MongoCollection.configureExisting(nc, definition)
            }
            return mapping
        }

    }
}
