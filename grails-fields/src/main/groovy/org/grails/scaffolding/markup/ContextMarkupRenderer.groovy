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

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Used to output context surrounding any given content. Context is any markup that will be rendered
 * along with any markup for domain property input or output. Input is used in this class to mean
 * any HTML input type element (A way to retrieve users input). Output is used in this class to mean
 * the display of a domain property on the page.
 *
 * An example of what might be returned with {@link #inputContext(DomainProperty,Closure)}
 * <pre>{@code
 * { ->
 *      div([class: "form-group"]) {
 *          label('', [for: property.name])
 *          content.delegate = delegate
 *          content.call()
 *      }}
 * }</pre>
 *
 * @author James Kleeh
 */
interface ContextMarkupRenderer {

    /**
     * Defines the context for rendering a list of domain class instances
     *
     * @param domainClass The domain class to be rendered
     * @param properties The properties to be rendered
     * @param content The content to be rendered for each property
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure listOutputContext(PersistentEntity domainClass, List<DomainProperty> properties, Closure content)

    /**
     * Defines the context for rendering a list of domain class properties inputs (form)
     *
     * @param domainClass The domain class to be rendered
     * @param content The content to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure inputContext(PersistentEntity domainClass, Closure content)

    /**
     * Defines the context for rendering a single domain class property input (select, textarea, etc)
     *
     * @param property The domain property to be rendered
     * @param content The content to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure inputContext(DomainProperty property, Closure content)

    /**
     * Defines the context for rendering a list domain class properties (show page)
     *
     * @param domainClass The domain class to be rendered
     * @param content The content to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure outputContext(PersistentEntity domainClass, Closure content)

    /**
     * Defines the context for rendering a single domain class property output
     *
     * @param property The domain property to be rendered
     * @param content The content to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure outputContext(DomainProperty property, Closure content)

    /**
     * Defines the context for rendering a the output of an embedded domain class property
     *
     * @param property The domain property to be rendered
     * @param content The content to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure embeddedOutputContext(DomainProperty property, Closure content)

    /**
     * Defines the context for rendering a the input of an embedded domain class property
     *
     * @param property The domain property to be rendered
     * @param content The content to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure embeddedInputContext(DomainProperty property, Closure content)

}
