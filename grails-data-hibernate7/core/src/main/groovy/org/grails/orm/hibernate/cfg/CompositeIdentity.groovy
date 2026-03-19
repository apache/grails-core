/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Property
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity
import org.hibernate.MappingException

/**
 * Represents a composite identity, equivalent to Hibernate <composite-id> mapping.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@AutoClone
@Builder(builderStrategy = SimpleStrategy, prefix = '')
@CompileStatic
class CompositeIdentity extends Property implements HibernateIdentity {

    /**
     * The property names that make up the custom identity
     */
    String[] propertyNames
    /**
     * The composite id class
     */
    Class compositeClass
    /**
     * The natural id definition
     */
    NaturalId natural

    /**
     * Define the natural id
     * @param naturalIdDef The callable
     * @return This id
     */
    CompositeIdentity naturalId(@DelegatesTo(NaturalId) Closure naturalIdDef) {
        this.natural = new NaturalId()
        naturalIdDef.setDelegate(this.natural)
        naturalIdDef.setResolveStrategy(Closure.DELEGATE_ONLY)
        naturalIdDef.call()
        return this
    }

    /**
     * @param domainClass The domain class
     * @return The hibernate properties for the composite identity
     */
    HibernatePersistentProperty[] getHibernateProperties(GrailsHibernatePersistentEntity domainClass) {
        HibernatePersistentProperty[] composite = propertyNames ?
                propertyNames.collect { domainClass.getHibernatePropertyByName(it) as HibernatePersistentProperty } as HibernatePersistentProperty[] :
                domainClass.compositeIdentity

        if (!composite) {
            throw new MappingException("No composite identifier properties found for class [${domainClass.name}]")
        }

        if (composite.any { it == null }) {
            throw new MappingException("Property referenced in composite-id mapping of class [${domainClass.name}] is not a valid property!")
        }

        if (composite.length == 0) {
            throw new MappingException("No composite identifier properties found for class [${domainClass.name}]")
        }

        composite
    }
}
