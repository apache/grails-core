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
 * The default renderer for rendering {@link Number} or primitive properties
 *
 * @author James Kleeh
 */
@CompileStatic
class NumberInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        Class type = domainProperty.type
        type.isPrimitive() || type in Number
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map attributes, DomainProperty property) {
        Constrained constraints = property.constrained
        Range range = constraints?.range
        if (range) {
            attributes.type = "range"
            attributes.min = range.from
            attributes.max = range.to
        } else {
            String typeName = property.type.simpleName.toLowerCase()

            attributes.type = "number"

            if(typeName in ['double', 'float', 'bigdecimal']) {
                attributes.step = "any"
            }
            if (constraints?.scale != null) {
                attributes.step = "0.${'0' * (constraints.scale - 1)}1"
            }
            if (constraints?.min != null) {
                attributes.min = constraints.min
            }
            if (constraints?.max != null) {
                attributes.max = constraints.max
            }
        }

        return { ->
            input(attributes)
        }
    }
}
