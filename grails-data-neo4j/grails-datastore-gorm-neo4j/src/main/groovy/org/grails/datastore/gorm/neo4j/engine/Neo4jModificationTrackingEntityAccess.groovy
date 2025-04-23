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

package org.grails.datastore.gorm.neo4j.engine

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.ModificationTrackingEntityAccess

/**
 * An extension of {@link ModificationTrackingEntityAccess} that stores
 * the old value instead of the new.
 *
 * @author James Kleeh
 * @since 6.1.5
 */
@CompileStatic
class Neo4jModificationTrackingEntityAccess extends ModificationTrackingEntityAccess {

    Neo4jModificationTrackingEntityAccess(EntityAccess target) {
        super(target)
    }

    @Override
    void setPropertyNoConversion(String name, Object value) {
        modifiedProperties.put(name, getProperty(name))
        target.setPropertyNoConversion(name, value)
    }

    /**
     * Sets a property value
     * @param name The name of the property
     * @param value The value of the property
     */
    @Override
    void setProperty(String name, Object value) {
        modifiedProperties.put(name, getProperty(name))
        target.setProperty(name, value)
    }
}
