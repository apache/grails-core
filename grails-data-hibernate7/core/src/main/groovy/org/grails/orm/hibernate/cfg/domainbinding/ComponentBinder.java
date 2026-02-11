package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.List;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;

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

    public void bindComponent(Component component, Embedded property,
                               boolean isNullable, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        Class<?> type = property.getType();
        String role = GrailsHibernateUtil.qualify(type.getName(), property.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) property.getAssociatedEntity();
        mappingCacheHolder.cacheMapping(domainClass);
        final List<PersistentProperty> properties = domainClass.getPersistentProperties();
        Table table = component.getOwner().getTable();
        PersistentClass persistentClass = component.getOwner();
        String path = property.getName();
        Class<?> propertyType = property.getOwner().getJavaClass();

        for (PersistentProperty currentGrailsProp : properties) {
            if (currentGrailsProp.equals(domainClass.getIdentity())) continue;
            if (currentGrailsProp.getName().equals(GormProperties.VERSION)) continue;

            if (currentGrailsProp.getType().equals(propertyType)) {
                component.setParentProperty(currentGrailsProp.getName());
                continue;
            }

            if (currentGrailsProp instanceof GrailsHibernatePersistentProperty) {
                componentPropertyBinder.bindComponentProperty(component, property, (GrailsHibernatePersistentProperty) currentGrailsProp, persistentClass, path,
                        table, mappings, sessionFactoryBeanName);
            } else {
                // Handle cases where currentGrailsProp is not a GrailsHibernatePersistentProperty
                // For now, we'll just skip binding for such properties in this test context
            }
        }
    }
}
