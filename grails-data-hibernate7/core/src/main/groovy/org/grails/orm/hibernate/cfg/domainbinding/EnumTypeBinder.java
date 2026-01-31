package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.IdentityEnumType;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.grails.orm.hibernate.HibernateLegacyEnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.util.Properties;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.ENUM_CLASS_PROP;
import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.ENUM_TYPE_CLASS;


public class EnumTypeBinder {

    private final IndexBinder indexBinder;
    private ColumnConfigToColumnBinder columnConfigToColumnBinder;

    public EnumTypeBinder() {
        this(new IndexBinder(), new ColumnConfigToColumnBinder());
    }

    protected EnumTypeBinder(IndexBinder indexBinder, ColumnConfigToColumnBinder columnConfigToColumnBinder) {
        this.indexBinder = indexBinder;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
    }


    private static final Logger LOG = LoggerFactory.getLogger(EnumTypeBinder.class);

    public void bindEnumType(PersistentProperty property, Class<?> propertyType, SimpleValue simpleValue, String columnName) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        Mapping ownerMapping = null;
        if (property.getOwner() instanceof GrailsHibernatePersistentEntity) {
            ownerMapping = ((GrailsHibernatePersistentEntity) property.getOwner()).getMappedForm();
        }
        String enumType = pc.getEnumType();
        Properties enumProperties = new Properties();
        enumProperties.put(ENUM_CLASS_PROP, propertyType.getName());
        String typeName = property instanceof GrailsHibernatePersistentProperty ghpp ? ghpp.getTypeName(ownerMapping) : null;
        if (typeName != null) {
            simpleValue.setTypeName(typeName);
        } else {
            if (GrailsEnumType.DEFAULT.getType().equals(enumType) || GrailsEnumType.STRING.getType().equalsIgnoreCase(enumType)) {
                simpleValue.setTypeName(ENUM_TYPE_CLASS);
                enumProperties.put(HibernateLegacyEnumType.TYPE, String.valueOf(Types.VARCHAR));
                enumProperties.put(HibernateLegacyEnumType.NAMED, Boolean.TRUE.toString());
            } else if (GrailsEnumType.ORDINAL.getType().equalsIgnoreCase(enumType)) {
                simpleValue.setTypeName(ENUM_TYPE_CLASS);
                enumProperties.put(HibernateLegacyEnumType.TYPE, String.valueOf(Types.INTEGER));
                enumProperties.put(HibernateLegacyEnumType.NAMED, Boolean.FALSE.toString());
            } else if (GrailsEnumType.IDENTITY.getType().equals(enumType)) {
                simpleValue.setTypeName(IdentityEnumType.class.getName());
            } else {
                throw new MappingException("Invalid enum type [" + enumType + "].");
            }

        }
        simpleValue.setTypeParameters(enumProperties);

        Column column = new Column();
        boolean isTablePerHierarchySubclass = !property.getOwner().isRoot() && (ownerMapping == null || ownerMapping.getTablePerHierarchy());
        if (isTablePerHierarchySubclass) {
            // Properties on subclasses in a table-per-hierarchy strategy must be nullable.
            if (LOG.isDebugEnabled()) {
                LOG.debug("[GrailsDomainBinder] Sub class property [{}] for column name [{}] forced to nullable",
                        property.getName(), columnName);
            }
            column.setNullable(true);
        }
        else {
            column.setNullable(property.isNullable());
        }

        column.setValue(simpleValue);
        column.setName(columnName);
        Table t = simpleValue.getTable();
        t.addColumn(column);
        simpleValue.addColumn(column);

        if (!pc.getColumns().isEmpty()) {
            ColumnConfig columnConfig = pc.getColumns().get(0);
            indexBinder.bindIndex(columnName, column, columnConfig, t);
            columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, pc);
        }
    }

}