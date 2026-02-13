package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.List;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;

import jakarta.annotation.Nonnull;

public class ComponentBinder {

    private final MappingCacheHolder mappingCacheHolder;
    private final ComponentPropertyBinder componentPropertyBinder;

    public ComponentBinder(MappingCacheHolder mappingCacheHolder, ComponentPropertyBinder componentPropertyBinder) {
        this.mappingCacheHolder = mappingCacheHolder;
        this.componentPropertyBinder = componentPropertyBinder;
    }

    protected ComponentBinder() {
        this.mappingCacheHolder = null;
        this.componentPropertyBinder = null;
    }

    public void bindComponent(Component component, HibernateEmbeddedProperty property,
                              @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        Class<?> type = property.getType();
        String role = GrailsHibernateUtil.qualify(type.getName(), property.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) property.getAssociatedEntity();
        mappingCacheHolder.cacheMapping(domainClass);
        var properties = domainClass.getHibernatePersistentProperties();
        Table table = component.getOwner().getTable();
        PersistentClass persistentClass = component.getOwner();
        String path = property.getName();
        Class<?> propertyType = property.getOwner().getJavaClass();

        for (GrailsHibernatePersistentProperty currentGrailsProp : properties) {
            if (currentGrailsProp.equals(domainClass.getIdentity())) continue;
            if (currentGrailsProp.getName().equals(GormProperties.VERSION)) continue;

            if (currentGrailsProp.getType().equals(propertyType)) {
                component.setParentProperty(currentGrailsProp.getName());
                continue;
            }
            componentPropertyBinder.bindComponentProperty(component, property, currentGrailsProp, persistentClass, path, table, mappings, sessionFactoryBeanName);
        }
    }
}
