package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Iterator;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

import jakarta.annotation.Nonnull;

public class ComponentPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentPropertyBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final MappingCacheHolder mappingCacheHolder;
    private final CollectionHolder collectionHolder;
    private final EnumTypeBinder enumTypeBinder;
    private final CollectionBinder collectionBinder;
    private final PropertyFromValueCreator propertyFromValueCreator;
    private final SimpleValueBinder simpleValueBinder;
    private final ComponentBinder componentBinder;

    public ComponentPropertyBinder(MetadataBuildingContext metadataBuildingContext,
                                   PersistentEntityNamingStrategy namingStrategy,
                                   MappingCacheHolder mappingCacheHolder,
                                   CollectionHolder collectionHolder,
                                   EnumTypeBinder enumTypeBinder,
                                   CollectionBinder collectionBinder,
                                   PropertyFromValueCreator propertyFromValueCreator) {
        this(metadataBuildingContext, namingStrategy, mappingCacheHolder, collectionHolder,
                enumTypeBinder, collectionBinder, propertyFromValueCreator, null, new SimpleValueBinder(namingStrategy));
    }

    protected ComponentPropertyBinder(MetadataBuildingContext metadataBuildingContext,
                                   PersistentEntityNamingStrategy namingStrategy,
                                   MappingCacheHolder mappingCacheHolder,
                                   CollectionHolder collectionHolder,
                                   EnumTypeBinder enumTypeBinder,
                                   CollectionBinder collectionBinder,
                                   PropertyFromValueCreator propertyFromValueCreator,
                                   ComponentBinder componentBinder,
                                   SimpleValueBinder simpleValueBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.mappingCacheHolder = mappingCacheHolder;
        this.collectionHolder = collectionHolder;
        this.enumTypeBinder = enumTypeBinder;
        this.collectionBinder = collectionBinder;
        this.propertyFromValueCreator = propertyFromValueCreator;
        this.componentBinder = componentBinder != null ? componentBinder : new ComponentBinder(mappingCacheHolder, this);
        this.simpleValueBinder = simpleValueBinder;
    }

    protected ComponentPropertyBinder() {
        this.metadataBuildingContext = null;
        this.namingStrategy = null;
        this.mappingCacheHolder = null;
        this.collectionHolder = null;
        this.enumTypeBinder = null;
        this.collectionBinder = null;
        this.propertyFromValueCreator = null;
        this.componentBinder = null;
        this.simpleValueBinder = null;
    }

    public void bindComponentProperty(Component component,
                                      PersistentProperty componentProperty,
                                       GrailsHibernatePersistentProperty currentGrailsProp,
                                      PersistentClass persistentClass,
                                       String path,
                                      Table table,
                                      @Nonnull InFlightMetadataCollector mappings,
                                      String sessionFactoryBeanName) {
        Value value;
        // see if it's a collection type
        CollectionType collectionType = collectionHolder.get(currentGrailsProp.getType());
        if (collectionType != null) {
            // create collection
            Collection collection = collectionType.create((HibernateToManyProperty) currentGrailsProp, persistentClass);
            collectionBinder.bindCollection((HibernateToManyProperty) currentGrailsProp, collection, persistentClass, mappings, path, sessionFactoryBeanName);
            mappings.addCollectionBinding(collection);
            value = collection;
        }
        // work out what type of relationship it is and bind value
        else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.ManyToOne) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as ManyToOne");

            value = new ManyToOne(metadataBuildingContext, table);
            new ManyToOneBinder(namingStrategy).bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path);
        } else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne association) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");

            if (association.canBindOneToOneWithSingleColumnAndForeignKey()) {
                value = new OneToOne(metadataBuildingContext, table, persistentClass);
                new OneToOneBinder(namingStrategy).bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, (OneToOne) value, path);
            }
            else {
                value = new ManyToOne(metadataBuildingContext, table);
                new ManyToOneBinder(namingStrategy).bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path);
            }
        }
        else if (currentGrailsProp instanceof Embedded embedded) {
            value = new Component(metadataBuildingContext, persistentClass);
            componentBinder.bindComponent((Component) value, embedded, true, mappings, sessionFactoryBeanName);
        }
        else {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as SimpleValue");

            value = new BasicValue(metadataBuildingContext, table);
            if (currentGrailsProp.getType().isEnum()) {
                String columnName = new ColumnNameForPropertyAndPathFetcher(namingStrategy).getColumnNameForPropertyAndPath(currentGrailsProp, path, null);
                enumTypeBinder.bindEnumType(currentGrailsProp, currentGrailsProp.getType(), (SimpleValue) value, columnName);
            }
            else {
                // set type
                this.simpleValueBinder.bindSimpleValue(currentGrailsProp, (GrailsHibernatePersistentProperty) componentProperty, (SimpleValue) value, path);
            }
        }

        Property persistentProperty = propertyFromValueCreator.createProperty(value, currentGrailsProp);
        component.addProperty(persistentProperty);
        if (componentProperty != null && componentProperty.getOwner() instanceof GrailsHibernatePersistentEntity ghpe && ghpe.isComponentPropertyNullable(componentProperty)) {
            final Iterator<?> columnIterator = value.getColumns().iterator();
            while (columnIterator.hasNext()) {
                Column c = (Column) columnIterator.next();
                c.setNullable(true);
            }
        }
    }

    public void bindComponent(Component component, Embedded property,
                               boolean isNullable, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        componentBinder.bindComponent(component, property, isNullable, mappings, sessionFactoryBeanName);
    }
}
