package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

import jakarta.annotation.Nonnull;

public class ComponentBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final MappingCacheHolder mappingCacheHolder;
    private final EnumTypeBinder enumTypeBinder;
    private final CollectionBinder collectionBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final OneToOneBinder oneToOneBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final SimpleValueBinder simpleValueBinder;
    private final ComponentUpdater componentUpdater;
    private GrailsPropertyBinder grailsPropertyBinder;

    public ComponentBinder(MetadataBuildingContext metadataBuildingContext,
                           MappingCacheHolder mappingCacheHolder,
                           EnumTypeBinder enumTypeBinder,
                           CollectionBinder collectionBinder,
                           SimpleValueBinder simpleValueBinder,
                           OneToOneBinder oneToOneBinder,
                           ManyToOneBinder manyToOneBinder,
                           ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
                           ComponentUpdater componentUpdater) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.mappingCacheHolder = mappingCacheHolder;
        this.enumTypeBinder = enumTypeBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.componentUpdater = componentUpdater;
    }

    public void setGrailsPropertyBinder(GrailsPropertyBinder grailsPropertyBinder) {
        this.grailsPropertyBinder = grailsPropertyBinder;
    }


    public Component bindComponent(PersistentClass owner, HibernateEmbeddedProperty embeddedProperty,
                              @Nonnull InFlightMetadataCollector mappings, String path) {
        Component component = new Component(metadataBuildingContext, owner);
        Class<?> type = embeddedProperty.getType();
        String role = GrailsHibernateUtil.qualify(type.getName(), embeddedProperty.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) embeddedProperty.getAssociatedEntity();
        mappingCacheHolder.cacheMapping(domainClass);
        var peerProperties = domainClass.getHibernatePersistentProperties();

        Table table = component.getOwner().getTable();
        PersistentClass persistentClass = component.getOwner();
        String currentPath = path.isEmpty() ? embeddedProperty.getName() : path + "." + embeddedProperty.getName();
        Class<?> propertyType = embeddedProperty.getOwner().getJavaClass();

        for (GrailsHibernatePersistentProperty peerProperty : peerProperties) {
            if (peerProperty.equals(domainClass.getIdentity())) continue;
            if (peerProperty.getName().equals(GormProperties.VERSION)) continue;

            if (peerProperty.getType().equals(propertyType)) {
                component.setParentProperty(peerProperty.getName());
                continue;
            }
            var value = grailsPropertyBinder.bindProperty(persistentClass, table, currentPath, embeddedProperty, peerProperty, mappings);
            componentUpdater.updateComponent(component, embeddedProperty, peerProperty, value);

        }
        return component;
    }
}
