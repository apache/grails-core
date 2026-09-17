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
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

// ComponentBinder holds a GrailsPropertyBinder reference set post-construction via
// setGrailsPropertyBinder() to break a circular dependency (ComponentBinder ↔ GrailsPropertyBinder ↔
// CollectionBinder ↔ ComponentBinder). A future major release can introduce a shared binding context or
// factory object that all binders receive at construction time.
/**
 * Binds embedded components and embedded collection elements to the Hibernate meta-model.
 *
 * @since 8.0
 */
@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class ComponentBinder {

    private final MetadataBuildingContext metadataBuildingContext
    private final MappingCacheHolder mappingCacheHolder
    private final ComponentUpdater componentUpdater
    private GrailsPropertyBinder grailsPropertyBinder

    ComponentBinder(
            MetadataBuildingContext metadataBuildingContext,
            MappingCacheHolder mappingCacheHolder,
            ComponentUpdater componentUpdater) {
        this.metadataBuildingContext = metadataBuildingContext
        this.mappingCacheHolder = mappingCacheHolder
        this.componentUpdater = componentUpdater
    }

    void setGrailsPropertyBinder(GrailsPropertyBinder grailsPropertyBinder) {
        this.grailsPropertyBinder = grailsPropertyBinder
    }

    Component bindComponent(@Nonnull HibernateEmbeddedProperty embeddedProperty, String path) {
        PersistentClass owner = embeddedProperty.persistentClass
        Component component = new Component(metadataBuildingContext, owner)
        Class<?> type = embeddedProperty.type
        String role = GrailsHibernateUtil.qualify(type.name, embeddedProperty.name)
        component.roleName = role
        component.componentClassName = type.name

        GrailsHibernatePersistentEntity associatedEntity =
                (GrailsHibernatePersistentEntity) embeddedProperty.associatedEntity
        mappingCacheHolder.cacheMapping(associatedEntity)

        PersistentClass persistentClass = component.owner
        associatedEntity.persistentClass = persistentClass
        String currentPath = path.isEmpty() ? embeddedProperty.name : "${path}.${embeddedProperty.name}"
        Class<?> propertyType = embeddedProperty.owner.javaClass

        associatedEntity
                .getHibernateParentProperty(propertyType)
                .ifPresent { p -> component.setParentProperty(p.name) }

        for (HibernatePersistentProperty peerProperty :
                associatedEntity.getHibernatePersistentProperties(propertyType)) {
            def value = grailsPropertyBinder.bindProperty(peerProperty, embeddedProperty, currentPath)
            componentUpdater.updateComponent(component, embeddedProperty, peerProperty, value)
        }
        return component
    }

    /**
     * Binds an embedded collection property as a Hibernate {@link Component} element.
     * Used for {@code hasMany} associations whose element type is a non-entity value object
     * (a GORM embedded type) rather than a scalar or persistent entity.
     */
    Component bindEmbeddedCollectionComponent(@Nonnull HibernateEmbeddedCollectionProperty property) {
        Collection collection = property.collection
        Component component = new Component(metadataBuildingContext, collection)

        GrailsHibernatePersistentEntity associatedEntity =
                (GrailsHibernatePersistentEntity) property.associatedEntity
        mappingCacheHolder.cacheMapping(associatedEntity)

        Class<?> elementType = property.componentType
        if (elementType == null) {
            elementType = property.type
        }
        component.componentClassName = elementType.name

        String role = GrailsHibernateUtil.qualify(property.owner.javaClass.name, property.name)
        component.roleName = role

        associatedEntity.persistentClass = collection.owner

        Class<?> ownerType = property.owner.javaClass
        for (HibernatePersistentProperty peer : associatedEntity.getHibernatePersistentProperties(ownerType)) {
            def value = grailsPropertyBinder.bindProperty(peer, null, property.name)
            componentUpdater.updateComponent(component, property, peer, value)
        }

        return component
    }

}
