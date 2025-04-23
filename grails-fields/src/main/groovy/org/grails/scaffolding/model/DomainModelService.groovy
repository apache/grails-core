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

package org.grails.scaffolding.model

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * An API to retrieve properties from a {@link PersistentEntity}
 *
 * @author James Kleeh
 */
interface DomainModelService {

    /**
     * The list of {@link DomainProperty} instances that allow for user input
     *
     * @param domainClass The persistent entity
     */
    List<DomainProperty> getInputProperties(PersistentEntity domainClass)
    List<DomainProperty> getInputProperties(PersistentEntity domainClass, List blackList)

    /**
     * The list of {@link DomainProperty} instances that are to be visible
     *
     * @param domainClass The persistent entity
     */
    List<DomainProperty> getOutputProperties(PersistentEntity domainClass)

    /**
     * The list of {@link DomainProperty} instances that are to be visible in a list context
     *
     * @param domainClass The persistent entity
     */
    List<DomainProperty> getListOutputProperties(PersistentEntity domainClass)

    /**
     * The list of {@link DomainProperty} instances that allow for user input and the closure returns true for
     *
     * @param domainClass The persistent entity
     * @param closure The closure that will be executed for each editable property
     */
    List<DomainProperty> findInputProperties(PersistentEntity domainClass, Closure closure)

    /**
     * Determines if the closure returns true for any input {@link DomainProperty}
     *
     * @param domainClass The persistent entity
     * @param closure The closure that will be executed for each property
     */
    Boolean hasInputProperty(PersistentEntity domainClass, Closure closure)

}
