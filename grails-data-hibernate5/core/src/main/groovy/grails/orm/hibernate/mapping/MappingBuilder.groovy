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

package grails.gorm.hibernate.mapping

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.MappingDefinition
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig

/**
 * Entry point for the ORM mapping configuration DSL
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class MappingBuilder {

    /**
     * Build a Hibernate mapping
     *
     * @param mappingDefinition The closure defining the mapping
     * @return The mapping
     */
    static MappingDefinition<Mapping, PropertyConfig> define(@DelegatesTo(Mapping) Closure mappingDefinition) {
        new ClosureMappingDefinition(mappingDefinition)
    }

    /**
     * Build a Hibernate mapping
     *
     * @param mappingDefinition The closure defining the mapping
     * @return The mapping
     */
    static MappingDefinition<Mapping, PropertyConfig> orm(@DelegatesTo(Mapping) Closure mappingDefinition) {
        new ClosureMappingDefinition(mappingDefinition)
    }

    @CompileStatic
    private static class ClosureMappingDefinition implements MappingDefinition<Mapping, PropertyConfig> {
        final Closure definition
        private Mapping mapping

        ClosureMappingDefinition(Closure definition) {
            this.definition = definition
        }

        @Override
        Mapping configure(Mapping existing) {
            return Mapping.configureExisting(existing, definition)
        }

        @Override
        Mapping build() {
            if(mapping == null) {
                mapping = Mapping.configureNew(definition)
            }
            return mapping
        }
    }
}
