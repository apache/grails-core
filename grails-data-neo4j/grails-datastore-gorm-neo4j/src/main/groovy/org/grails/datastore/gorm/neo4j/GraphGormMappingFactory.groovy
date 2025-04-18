/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.neo4j

import org.grails.datastore.gorm.neo4j.mapping.config.Attribute
import org.grails.datastore.gorm.neo4j.mapping.config.NodeConfig
import org.grails.datastore.gorm.neo4j.mapping.config.RelationshipConfig
import org.grails.datastore.mapping.config.AbstractGormMappingFactory
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.model.PersistentEntity

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
        if(entity instanceof RelationshipPersistentEntity) {
            Class previousType = currentNodeConfigType
            try {
                currentNodeConfigType = RelationshipConfig
                return super.createMappedForm(entity)
            } finally {
                currentNodeConfigType = previousType
            }
        }
        else {
            return super.createMappedForm(entity)
        }
    }
}


