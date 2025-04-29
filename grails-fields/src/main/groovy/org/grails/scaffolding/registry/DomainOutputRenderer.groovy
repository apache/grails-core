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

package org.grails.scaffolding.registry

import org.grails.scaffolding.model.property.DomainProperty

/**
 * Used to render markup that represents how single domain class property will be displayed
 *
 * @author James Kleeh
 */
interface DomainOutputRenderer extends DomainRenderer {

    /**
     * Defines how a given domain class property will be rendered in the context of a list of domains class instances
     *
     * @param property The domain property to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure renderListOutput(DomainProperty property)

    /**
     * Defines how a given domain class property will be rendered in the context of a single domain class instance
     *
     * @param property The domain property to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure renderOutput(DomainProperty property)
}
