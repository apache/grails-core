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

package org.grails.scaffolding.markup

import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Used to output markup that represents a given domain class.
 *
 * @author James Kleeh
 */
interface DomainMarkupRenderer {

    /**
     * Designed to render a "show" page that will display a single domain class instance.
     *
     * @param domainClass The domain class to be rendered
     * @return The rendered html
     */
    String renderOutput(PersistentEntity domainClass)

    /**
     * Designed to render a "list" page that will display a list of domain class instances.
     *
     * @param domainClass The domain class to be rendered
     * @return The rendered html
     */
    String renderListOutput(PersistentEntity domainClass)


    /**
     * Designed to render a form that will allow users to create or edit domain class instances.
     *
     * @param domainClass The domain class to be rendered
     * @return The rendered html
     */
    String renderInput(PersistentEntity domainClass)

}