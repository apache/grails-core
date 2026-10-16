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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import groovy.transform.CompileStatic
import org.hibernate.mapping.DependantValue

import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH

/** Binds a dependent key value for collection associations. */
@CompileStatic
class DependentKeyValueBinder {

    private final SimpleValueBinder simpleValueBinder
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder

    DependentKeyValueBinder(
            SimpleValueBinder simpleValueBinder,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder) {
        this.simpleValueBinder = simpleValueBinder
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder
    }

    void bind(HibernateToManyProperty property, DependantValue key) {
        GrailsHibernatePersistentEntity refDomainClass = property.hibernateOwner

        Optional<HibernateCompositeIdentity> compositeIdentity = property.supportsJoinColumnMapping() ?
                refDomainClass.hibernateCompositeIdentity :
                Optional.empty()

        compositeIdentity.ifPresentOrElse(
                { HibernateCompositeIdentity ci ->
                    compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
                            property, key, ci, refDomainClass, EMPTY_PATH)
                },
                { -> simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH) })
    }

}
