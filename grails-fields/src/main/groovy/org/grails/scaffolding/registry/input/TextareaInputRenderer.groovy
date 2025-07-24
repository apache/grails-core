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

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering properties with the constraint {@code [widget: "textarea"]}
 *
 * @author James Kleeh
 */
@CompileStatic
class TextareaInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        domainProperty.constrained?.widget == "textarea"
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map defaultAttributes, DomainProperty domainProperty) {
        Integer maxSize = domainProperty.constrained?.maxSize
        if (maxSize) {
            defaultAttributes.maxlength = maxSize
        }
        return { ->
            textarea(defaultAttributes)
        }
    }
}
