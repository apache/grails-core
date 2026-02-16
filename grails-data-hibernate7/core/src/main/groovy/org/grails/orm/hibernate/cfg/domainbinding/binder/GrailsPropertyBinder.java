package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyWrapper;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class GrailsPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final CollectionHolder collectionHolder;
    private final EnumTypeBinder enumTypeBinder;
    private final ComponentBinder componentBinder;
    private final CollectionBinder collectionBinder;
    private final SimpleValueBinder simpleValueBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final OneToOneBinder oneToOneBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final PropertyFromValueCreator propertyFromValueCreator;

    public GrailsPropertyBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            CollectionHolder collectionHolder,
            EnumTypeBinder enumTypeBinder,
            ComponentBinder componentBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder,
            PropertyFromValueCreator propertyFromValueCreator) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.collectionHolder = collectionHolder;
        this.enumTypeBinder = enumTypeBinder;
        this.componentBinder = componentBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.propertyFromValueCreator = propertyFromValueCreator;
    }

    public Value bindProperty(PersistentClass persistentClass
            , @Nonnull GrailsHibernatePersistentProperty currentGrailsProp
            , @Nonnull InFlightMetadataCollector mappings) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsPropertyBinder] Binding persistent property [" + currentGrailsProp.getName() + "]");
        }
        Mapping gormMapping =  currentGrailsProp.getHibernateOwner().getMappedForm();
        Table table = persistentClass.getTable();
        table.setComment(gormMapping.getComment());

        Value value = null;

        // see if it's a collection type
        CollectionType collectionType = collectionHolder.get(currentGrailsProp.getType());


        // 1. Create Value and apply binders (consolidated block)
        if (currentGrailsProp.isUserButNotCollectionType()) {
            value = new BasicValue(metadataBuildingContext, table);
            simpleValueBinder.bindSimpleValue(currentGrailsProp, null,(SimpleValue) value, EMPTY_PATH);
        }
        else if (collectionType != null) {
            if (currentGrailsProp.isSerializableType()) {
                value = new BasicValue(metadataBuildingContext, table);
                simpleValueBinder.bindSimpleValue(currentGrailsProp, null,(SimpleValue) value, EMPTY_PATH);
            }
            else { // Actual Collection
                Collection collection = collectionType.create((HibernateToManyProperty) currentGrailsProp, persistentClass);
                collectionBinder.bindCollection((HibernateToManyProperty) currentGrailsProp, collection, persistentClass, mappings, EMPTY_PATH);
                mappings.addCollectionBinding(collection);
                value = collection;
            }
        }
        else if (currentGrailsProp.getType().isEnum()) {
            value = new BasicValue(metadataBuildingContext, table);
            SimpleValue simpleValue = (SimpleValue) value;
            String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(currentGrailsProp, EMPTY_PATH, null);
            enumTypeBinder.bindEnumType(currentGrailsProp, currentGrailsProp.getType(), simpleValue, columnName);
        }
        else if (currentGrailsProp.isHibernateOneToOne()) {
            value = new OneToOne(metadataBuildingContext, table, persistentClass);
            oneToOneBinder.bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne)currentGrailsProp, (OneToOne)value, EMPTY_PATH);
        } else if(currentGrailsProp.isHibernateManyToOne()) {
            value = manyToOneBinder.bindManyToOne((Association)currentGrailsProp, table, EMPTY_PATH);
        }
        else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
            value = componentBinder.bindComponent(persistentClass, embedded, mappings);
        }
        // work out what type of relationship it is and bind value
        else { // Default BasicValue
            value = new BasicValue(metadataBuildingContext, table);
            simpleValueBinder.bindSimpleValue(currentGrailsProp, null,(SimpleValue) value, EMPTY_PATH);
        }

        return value;
    }
}
