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

package org.grails.validation.discovery

import grails.validation.Constrained
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Strategy interface for discovering the {@link grails.validation.Constrained} properties of a class
 *
 * @author Graeme Rocher
 * @since 6.1
 */
interface ConstrainedDiscovery {

    /**
     * Finds the constrained properties for the given entity
     *
     * @param entity The entity
     * @return The constrained properties
     */
    Map<String, Constrained> findConstrainedProperties(PersistentEntity entity)
}