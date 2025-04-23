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

package org.grails.scaffolding.registry.output

import grails.util.GrailsNameUtils
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainOutputRenderer

/**
 * The renderer chosen for displaying domain properties when no other
 * renderers support the given property
 *
 * @author James Kleeh
 */
class DefaultOutputRenderer implements DomainOutputRenderer {

    protected String buildPropertyPath(DomainProperty property) {
        StringBuilder sb = new StringBuilder()
        sb.append(GrailsNameUtils.getPropertyName(property.rootBeanType)).append('.')
        sb.append(property.pathFromRoot)
        sb.toString()
    }

    @Override
    boolean supports(DomainProperty property) {
        true
    }

    @Override
    Closure renderListOutput(DomainProperty property) {
        renderOutput(property)
    }

    @Override
    Closure renderOutput(DomainProperty property) {
        { ->
            span("\${${buildPropertyPath(property)}}")
        }
    }
}
