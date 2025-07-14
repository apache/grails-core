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

package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering {@link String} properties
 *
 * @author James Kleeh
 */
@CompileStatic
class StringInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        domainProperty.type in [String, null]
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map standardAttributes, DomainProperty domainProperty) {
        Constrained constraints = domainProperty.constrained
        if (constraints?.password) {
            standardAttributes.type = "password"
        } else if (constraints?.email) {
            standardAttributes.type = "email"
        } else if (constraints?.url) {
            standardAttributes.type = "url"
        } else {
            standardAttributes.type = "text"
        }

        if (constraints?.matches) {
            standardAttributes.pattern = constraints.matches
        }
        if (constraints?.maxSize) {
            standardAttributes.maxlength = constraints.maxSize
        }

        return { ->
            input(standardAttributes)
        }
    }
}
