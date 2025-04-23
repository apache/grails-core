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
import grails.util.GrailsNameUtils
import grails.web.mapping.LinkGenerator
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.ToMany

/**
 * The default renderer for rendering bidirectional to many associations
 *
 * @author James Kleeh
 */
class BidirectionalToManyInputRenderer implements DomainInputRenderer {

    protected LinkGenerator linkGenerator

    BidirectionalToManyInputRenderer(LinkGenerator linkGenerator) {
        this.linkGenerator = linkGenerator
    }

    @Override
    boolean supports(DomainProperty property) {
        PersistentProperty persistentProperty = property.persistentProperty
        persistentProperty instanceof ToMany && persistentProperty.bidirectional
    }

    protected String getPropertyName(DomainProperty property) {
        GrailsNameUtils.getPropertyName(property.rootBeanType)
    }

    protected String getAssociatedClassName(DomainProperty property) {
        property.associatedType.simpleName
    }

    @Override
    Closure renderInput(Map defaultAttributes, DomainProperty property) {
        final String objectName = "${getPropertyName(property)}.id"
        defaultAttributes.remove('required')
        defaultAttributes.remove('readonly')
        defaultAttributes.href = linkGenerator.link(resource: property.associatedType, action: "create", params: [(objectName): ""])
        return { ->
            a("Add ${getAssociatedClassName(property)}", defaultAttributes)
        }
    }
}
