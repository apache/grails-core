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

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

/**
 * @see {@link DomainPropertyFactory}
 * @author James Kleeh
 */
@CompileStatic
class DomainPropertyFactoryImpl implements DomainPropertyFactory {

    @Value('${grails.databinding.convertEmptyStringsToNull:true}')
    Boolean convertEmptyStringsToNull

    @Value('${grails.databinding.trimStrings:true}')
    Boolean trimStrings

    @Autowired
    MappingContext grailsDomainClassMappingContext

    DomainProperty build(PersistentProperty persistentProperty) {
        DomainPropertyImpl domainProperty = new DomainPropertyImpl(persistentProperty, grailsDomainClassMappingContext)
        init(domainProperty)
        domainProperty
    }

    DomainProperty build(PersistentProperty rootProperty, PersistentProperty persistentProperty) {
        DomainPropertyImpl domainProperty = new DomainPropertyImpl(rootProperty, persistentProperty, grailsDomainClassMappingContext)
        init(domainProperty)
        domainProperty
    }

    private init(DomainPropertyImpl domainProperty) {
        domainProperty.convertEmptyStringsToNull = convertEmptyStringsToNull
        domainProperty.trimStrings = trimStrings
    }
}
