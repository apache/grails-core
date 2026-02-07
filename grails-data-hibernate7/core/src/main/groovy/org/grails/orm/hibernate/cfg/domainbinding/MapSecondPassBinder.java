package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.type.StandardBasicTypes;

import java.util.List;
import java.util.Map;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.UNDERSCORE;

/**
 * Refactored from CollectionBinder to handle map second pass binding.
 */
public class MapSecondPassBinder {
    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final CollectionBinder collectionBinder;

    public MapSecondPassBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, CollectionBinder collectionBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.collectionBinder = collectionBinder;
    }

    public void bindMapSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                  Map<?, ?> persistentClasses, org.hibernate.mapping.Map map, String sessionFactoryBeanName) {
        collectionBinder.bindCollectionSecondPass(property, mappings, persistentClasses, map, sessionFactoryBeanName);

        SimpleValue value = new BasicValue(metadataBuildingContext, map.getCollectionTable());

        String type = ((GrailsHibernatePersistentProperty) property).getIndexColumnType("string");
        String columnName1 = collectionBinder.getIndexColumnName(property);
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
            String columnName = getMapElementName(property, sessionFactoryBeanName);
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

    private String getMapElementName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = property instanceof GrailsHibernatePersistentProperty ghpp ? ghpp.getMappedForm() : new PropertyConfig();

        if (hasJoinTableColumnNameMapping(pc)) {
            return pc.getJoinTable().getColumn().getName();
        }
        return namingStrategy.resolveColumnName(property.getName()) + UNDERSCORE + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME;
    }

    private boolean hasJoinTableColumnNameMapping(PropertyConfig pc) {
        return pc != null && pc.getJoinTable() != null && pc.getJoinTable().getColumn() != null && pc.getJoinTable().getColumn().getName() != null;
    }
}