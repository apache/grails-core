package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import jakarta.annotation.Nonnull;

import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.type.StandardBasicTypes;

import java.util.List;
import java.util.Map;

/**
 * Refactored from CollectionBinder to handle map second pass binding.
 */
public class MapSecondPassBinder {
    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final CollectionSecondPassBinder collectionSecondPassBinder;

    public MapSecondPassBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, CollectionSecondPassBinder collectionSecondPassBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.collectionSecondPassBinder = collectionSecondPassBinder;
    }

    public void bindMapSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                  Map<?, ?> persistentClasses, org.hibernate.mapping.Map map, String sessionFactoryBeanName) {
        collectionSecondPassBinder.bindCollectionSecondPass(property, mappings, persistentClasses, map, sessionFactoryBeanName);
        SimpleValue value = new BasicValue(metadataBuildingContext, map.getCollectionTable());

        String type = ((GrailsHibernatePersistentProperty) property).getIndexColumnType("string");
        String columnName1 = property.getIndexColumnName(namingStrategy);
        new SimpleValueColumnBinder().bindSimpleValue(value, type, columnName1, true);
        PropertyConfig mappedForm = property.getMappedForm();
        if (mappedForm.getIndexColumn() != null) {
            Column column = new SimpleValueColumnFetcher().getColumnForSimpleValue(value);
            ColumnConfig columnConfig = getSingleColumnConfig(mappedForm.getIndexColumn());
            new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
        }

        if (!value.isTypeSpecified()) {
            throw new MappingException("map index element must specify a type: " + map.getRole());
        }
        map.setIndex(value);

        if(!(property instanceof org.grails.datastore.mapping.model.types.OneToMany) && !(property instanceof org.grails.datastore.mapping.model.types.ManyToMany)) {

            SimpleValue elt = new BasicValue(metadataBuildingContext, map.getCollectionTable());
            map.setElement(elt);

            Mapping mapping = null;
            GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) property.getOwner();
            if (domainClass != null) {
                mapping = domainClass.getMappedForm();
            }
            String typeName = property.getTypeName();
            if (typeName == null ) {

                if(property instanceof Basic) {
                    Basic basic = (Basic) property;
                    typeName = basic.getComponentType().getName();
                }
            }
            if(typeName == null || typeName.equals(Object.class.getName())) {
                typeName = StandardBasicTypes.STRING.getName();
            }
            String columnName = property.getMapElementName(namingStrategy);
            new SimpleValueColumnBinder().bindSimpleValue(elt, typeName, columnName, false);

            elt.setTypeName(typeName);
        }

        map.setInverse(false);
    }

    private ColumnConfig getSingleColumnConfig(PropertyConfig propertyConfig) {
        if (propertyConfig != null) {
            List<ColumnConfig> columns = propertyConfig.getColumns();
            if (columns != null && !columns.isEmpty()) {
                return columns.get(0);
            }
        }
        return null;
    }
}
