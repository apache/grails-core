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
package org.grails.datastore.gorm.neo4j

import org.grails.datastore.gorm.neo4j.mapping.config.Attribute
import org.grails.datastore.gorm.neo4j.mapping.config.NodeConfig
import org.grails.datastore.gorm.neo4j.mapping.config.RelationshipConfig
import org.grails.datastore.mapping.config.AbstractGormMappingFactory
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.DefaultIdentityMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.ValueGenerator

/**
 * A {@link org.grails.datastore.mapping.model.MappingFactory} for Neo4j
 *
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 * @author Graeme Rocher
 */
class GraphGormMappingFactory extends AbstractGormMappingFactory {

    private Class currentNodeConfigType = NodeConfig

    @Override
    protected Class getPropertyMappedFormType() {
        Attribute
    }

    @Override
    protected Class getEntityMappedFormType() {
        return currentNodeConfigType
    }

    @Override
    Entity createMappedForm(PersistentEntity entity) {
        if (entity instanceof RelationshipPersistentEntity) {
            Class previousType = currentNodeConfigType
            try {
                currentNodeConfigType = RelationshipConfig
                return super.createMappedForm(entity)
            } finally {
                currentNodeConfigType = previousType
            }
        } else {
            return super.createMappedForm(entity)
        }
    }

    /**
     * {@link MappingFactory#createDefaultIdentityMapping} resolves {@code generator} via
     * {@link ValueGenerator#valueOf} with no fallback, so any name that isn't one of its built-in
     * constants throws IllegalArgumentException. Unlike Hibernate - where the generator name is
     * always either a known strategy or a Hibernate generator class - Neo4j also accepts its own
     * built-in named strategies (e.g. "snowflake", see {@link IdGenerator.Type}) that aren't classes
     * at all and are resolved independently by {@link GraphPersistentEntity}; ValueGenerator here only
     * needs to not blow up, so any unrecognized name falls back to CUSTOM rather than throwing.
     */
    protected IdentityMapping createDefaultIdentityMapping(ClassMapping classMapping, Property property) {
        String targetName = property != null ? property.name : null
        String[] identifierNames = targetName != null ? [targetName] as String[] : [MappingFactory.IDENTITY_PROPERTY] as String[]
        String generatorName = property != null ? property.generator : null
        ValueGenerator generator
        if (generatorName != null) {
            try {
                generator = ValueGenerator.valueOf(generatorName.toUpperCase(Locale.ENGLISH))
            } catch (IllegalArgumentException ignored) {
                generator = ValueGenerator.CUSTOM
            }
        } else {
            generator = ValueGenerator.AUTO
        }
        new DefaultIdentityMapping(classMapping, property, identifierNames, generator)
    }
}
