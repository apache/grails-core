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

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering enum properties
 *
 * @author James Kleeh
 */
class EnumInputRenderer implements DomainInputRenderer {

    protected List<Map> getEnumValues(DomainProperty property) {
        List<Map> enumList = []
        List keys = property.type.values()*.name()
        List values = property.type.values()
        keys.eachWithIndex { k, i ->
            enumList.add([id: k, name: values[i].toString()])
        }
        enumList
    }

    @Override
    boolean supports(DomainProperty property) {
        property.type.isEnum()
    }

    @Override
    Closure renderInput(Map defaultAttributes, DomainProperty property) {
        List<Map> enumList = getEnumValues(property)

        return { ->
            select(defaultAttributes) {
                enumList.each {
                    option(it.name, [value: it.id])
                }
            }
        }
    }
}
