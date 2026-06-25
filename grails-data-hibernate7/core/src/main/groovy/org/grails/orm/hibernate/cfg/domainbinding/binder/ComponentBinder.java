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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.jspecify.annotations.NonNull;

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

// TODO (Hibernate 8 refactor): ComponentBinder holds a GrailsPropertyBinder reference set post-construction
// via setGrailsPropertyBinder() to break a circular dependency (ComponentBinder ↔ GrailsPropertyBinder ↔
// CollectionBinder ↔ ComponentBinder). This mutual dependency should be resolved by introducing a shared
// binding context or factory object that all binders receive at construction time.
/**
 * Binds embedded components and embedded collection elements to the Hibernate meta-model.
 *
 * @since 8.0
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ComponentBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final MappingCacheHolder mappingCacheHolder;
    private final ComponentUpdater componentUpdater;
    private GrailsPropertyBinder grailsPropertyBinder;

    public ComponentBinder(
            MetadataBuildingContext metadataBuildingContext,
            MappingCacheHolder mappingCacheHolder,
            ComponentUpdater componentUpdater) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.mappingCacheHolder = mappingCacheHolder;
        this.componentUpdater = componentUpdater;
    }

    public void setGrailsPropertyBinder(GrailsPropertyBinder grailsPropertyBinder) {
        this.grailsPropertyBinder = grailsPropertyBinder;
    }

    public Component bindComponent(@NonNull HibernateEmbeddedProperty embeddedProperty, String path) {
        var owner = embeddedProperty.getPersistentClass();
        Component component = new Component(metadataBuildingContext, owner);
        Class<?> type = embeddedProperty.getType();
        String role = GrailsHibernateUtil.qualify(type.getName(), embeddedProperty.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        GrailsHibernatePersistentEntity associatedEntity =
                (GrailsHibernatePersistentEntity) embeddedProperty.getAssociatedEntity();
        mappingCacheHolder.cacheMapping(associatedEntity);

        PersistentClass persistentClass = component.getOwner();
        associatedEntity.setPersistentClass(persistentClass);
        String currentPath = path.isEmpty() ? embeddedProperty.getName() : path + "." + embeddedProperty.getName();
        Class<?> propertyType = embeddedProperty.getOwner().getJavaClass();

        associatedEntity
                .getHibernateParentProperty(propertyType)
                .ifPresent(p -> component.setParentProperty(p.getName()));

        for (HibernatePersistentProperty peerProperty :
                associatedEntity.getHibernatePersistentProperties(propertyType)) {
            var value = grailsPropertyBinder.bindProperty(peerProperty, embeddedProperty, currentPath);
            componentUpdater.updateComponent(component, embeddedProperty, peerProperty, value);
        }
        return component;
    }

    /**
     * Binds an embedded collection property as a Hibernate {@link Component} element.
     * Used for {@code hasMany} associations whose element type is a non-entity value object
     * (a GORM embedded type) rather than a scalar or persistent entity.
     */
    public Component bindEmbeddedCollectionComponent(@NonNull HibernateEmbeddedCollectionProperty property) {
        Collection collection = property.getCollection();
        Component component = new Component(metadataBuildingContext, collection);

        GrailsHibernatePersistentEntity associatedEntity =
                (GrailsHibernatePersistentEntity) property.getAssociatedEntity();
        mappingCacheHolder.cacheMapping(associatedEntity);

        Class<?> elementType = property.getComponentType();
        if (elementType == null) {
            elementType = property.getType();
        }
        component.setComponentClassName(elementType.getName());

        String role = GrailsHibernateUtil.qualify(property.getOwner().getJavaClass().getName(), property.getName());
        component.setRoleName(role);

        associatedEntity.setPersistentClass(collection.getOwner());

        Class<?> ownerType = property.getOwner().getJavaClass();
        for (HibernatePersistentProperty peer : associatedEntity.getHibernatePersistentProperties(ownerType)) {
            var value = grailsPropertyBinder.bindProperty(peer, null, property.getName());
            componentUpdater.updateComponent(component, property, peer, value);
        }

        return component;
    }
}
