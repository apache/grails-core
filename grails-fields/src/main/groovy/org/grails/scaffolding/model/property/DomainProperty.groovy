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

package org.grails.scaffolding.model.property

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty

/**
 * An API to join the {@link PersistentProperty} to the {@link org.springframework.validation.Validator}
 * to assist with scaffolding
 *
 * @author James Kleeh
 */
interface DomainProperty extends PersistentProperty, Comparable<DomainProperty> {

    /**
     * @return The path of the property from the root domain class
     */
    String getPathFromRoot()

    /**
     * @return The {@link PersistentProperty} that represents this property
     */
    PersistentProperty getPersistentProperty()

    /**
     * @return The {@link PersistentEntity} the property belongs to
     */
    PersistentEntity getDomainClass()

    /**
     * @return The constraints of the property
     */
    Constrained getConstrained()

    /**
     * @return The root property
     */
    PersistentProperty getRootProperty()

    /**
     * Sets the root property
     *
     * @param rootProperty The root property
     */
    void setRootProperty(PersistentProperty rootProperty)

    /**
     * @return The class the root property belongs to
     */
    Class getRootBeanType()

    /**
     * @return The class the property belongs to
     */
    Class getBeanType()

    /**
     * @return The type of the association
     */
    Class getAssociatedType()

    /**
     * @return The associated entity if the property is an assocation
     */
    PersistentEntity getAssociatedEntity()

    /**
     * @return Whether or not the property is required
     */
    boolean isRequired()

    /**
     * @return i18n message keys to resolve the label of the property
     */
    List<String> getLabelKeys()

    /**
     * @return The default label for the property (natural name)
     */
    String getDefaultLabel()

}