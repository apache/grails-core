/*
 * Copyright 2015 original authors
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
package org.grails.datastore.gorm.neo4j.mapping.config

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy;
import org.grails.datastore.mapping.config.Entity;

/**
 * Extends the default {@link Entity} configuration adding the ability to assign labels
 *
 * @author Stefan Armbruster
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class NodeConfig extends Entity<Attribute> {

    /**
     * @return The label definitions for the entity
     */
    Object labels
    /**
     * Whether this entity supports dynamic associations. The default is false. Setting this to true will allow Grails to load dynamic relationships, however
     * at the cost of N+1. For each loaded entity Grails has to execute a separate query to establish the associations. This is contrary to non-dynamic associations
     * which can be loaded using an OPTIONAL MATCH
     *
     * @return True if the entity supports dynamic associations
     */
    boolean dynamicAssociations

    Boolean autoLabel

    @Override
    protected Attribute newProperty() {
        return new Attribute()
    }
}
