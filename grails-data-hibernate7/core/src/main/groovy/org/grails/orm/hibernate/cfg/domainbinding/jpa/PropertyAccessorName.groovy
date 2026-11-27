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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import groovy.transform.CompileStatic
import org.codehaus.groovy.transform.trait.Traits
import org.hibernate.boot.spi.AccessType

import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

/**
 * Decides how Hibernate reads and writes a property of a real domain instance: the mapping's {@code accessType}
 * ({@code property} unless the mapping says {@code field}), and the trait accessor for a trait-implemented getter read by
 * field. {@link GeneratedDomainClassBinder} puts the name on the bound {@code Property} once the generated classes are bound,
 * since the real class is what Hibernate instantiates.
 *
 * @since 9.0
 */
@CompileStatic
class PropertyAccessorName {

    /**
     * @param persistentProperty the GORM property
     * @return the Hibernate property accessor name
     */
    String accessorName(HibernatePersistentProperty persistentProperty) {
        PropertyConfig config = persistentProperty.hibernateMappedForm
        AccessType accessType = AccessType.getAccessStrategy(config != null ? config.accessType : new PropertyConfig().accessType)
        return accessType == AccessType.FIELD ?
                Optional.ofNullable(persistentProperty.reader)
                        .map { EntityReflector.PropertyReader reader -> reader.getter() }
                        .map { getter -> getter.getAnnotation(Traits.Implemented) }
                        .map { annotation -> TraitPropertyAccessStrategy.name }
                        .orElse(accessType.type) :
                accessType.type
    }

}
