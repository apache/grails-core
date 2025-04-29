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

package org.grails.datastore.gorm.neo4j.collection

import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.schemaless.DynamicAttributes

/**
 * Default implementation of the {@link Relationship} trait
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class Neo4jRelationship<F, T> implements Relationship<F, T> {
    String type

    Neo4jRelationship(F from, T to, String type) {
        this.from = from
        this.to = to
        this.type = type
    }

    Neo4jRelationship(F from, T to, org.neo4j.driver.types.Relationship neoRel) {
        this.from = from
        this.to = to
        this.type = neoRel.type()
        this.id = neoRel.id()
    }
    /**
     * Allows accessing to dynamic properties with the dot operator
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    def propertyMissing(String name) {
        return getAt(name)
    }

    /**
     * Allows setting a dynamic property via the dot operator
     * @param instance The instance
     * @param name The property name
     * @param val The value
     */
    def propertyMissing(String name, val) {
        putAt(name, val)
    }
}
