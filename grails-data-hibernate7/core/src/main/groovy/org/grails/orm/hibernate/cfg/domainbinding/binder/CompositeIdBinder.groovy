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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.MappingException
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Value

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCompositeIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class CompositeIdBinder {

    private final MetadataBuildingContext metadataBuildingContext
    private final ComponentUpdater componentUpdater
    private final GrailsPropertyBinder grailsPropertyBinder

    CompositeIdBinder(
            MetadataBuildingContext metadataBuildingContext,
            ComponentUpdater componentUpdater,
            GrailsPropertyBinder grailsPropertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext
        this.componentUpdater = componentUpdater
        this.grailsPropertyBinder = grailsPropertyBinder
    }

    void bindCompositeId(@Nonnull HibernatePersistentEntity domainClass) {
        if (domainClass.identityProperty instanceof HibernateCompositeIdentityProperty) {
            HibernateCompositeIdentityProperty compositeIdentityProperty =
                    (HibernateCompositeIdentityProperty) domainClass.identityProperty
            Component id = getComponent(domainClass)

            for (HibernatePersistentProperty property : compositeIdentityProperty.parts) {
                Value value = grailsPropertyBinder.bindProperty(property, null, '')
                componentUpdater.updateComponent(id, null, property, value)
            }
            return
        }
        throw new MappingException("Invalid composite id binding for entity [${domainClass.name}]".toString())
    }

    private Component getComponent(HibernatePersistentEntity domainClass) {
        RootClass rootClass = domainClass.rootClass
        Component id = new Component(metadataBuildingContext, rootClass)
        id.nullValue = 'undefined'
        rootClass.identifier = id
        rootClass.embeddedIdentifier = true
        id.componentClassName = domainClass.name
        id.key = true
        id.embedded = true

        String path = GrailsHibernateUtil.qualify(rootClass.entityName, 'id')

        id.roleName = path
        return id
    }

}
