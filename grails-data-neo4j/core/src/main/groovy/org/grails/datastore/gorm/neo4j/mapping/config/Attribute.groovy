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

package org.grails.datastore.gorm.neo4j.mapping.config

import grails.neo4j.Direction
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Property

/**
 * Represents a Neo4j attribute or relationships config
 *
 * @author Graeme Rocher
 * @since 6.0.5
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Attribute extends Property {

    /**
     * The relationship direction
     */
    Direction direction
    /**
     * Sets the relationship type
     *
     * @param relationshipType The relationship type
     */
    void setType(String relationshipType) {
        setTargetName(relationshipType)
    }

    /**
     * @return The relationship type
     */
    String getType() {
        getTargetName()
    }

    /**
     * Sets the relationship type
     *
     * @param relationshipType The relationship type
     */
    Attribute type(String relationshipType) {
        setType(relationshipType)
        return this
    }

}
