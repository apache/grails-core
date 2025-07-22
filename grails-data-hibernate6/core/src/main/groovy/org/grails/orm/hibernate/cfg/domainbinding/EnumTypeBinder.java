package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.IdentityEnumType;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.type.EnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.util.Properties;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.DEFAULT_ENUM_TYPE;
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
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);
        Mapping ownerMapping = new HibernateEntityWrapper(property.getOwner()).getMappedForm();
        String enumType = pc.getEnumType();
        Properties enumProperties = new Properties();
        enumProperties.put(ENUM_CLASS_PROP, propertyType.getName());
        String typeName = new TypeNameProvider().getTypeName(property, ownerMapping);
        if (typeName != null) {
            simpleValue.setTypeName(typeName);
        } else {
            if (DEFAULT_ENUM_TYPE.equals(enumType) || "string".equalsIgnoreCase(enumType)) {
                simpleValue.setTypeName(ENUM_TYPE_CLASS);
                enumProperties.put(EnumType.TYPE, String.valueOf(Types.VARCHAR));
                enumProperties.put(EnumType.NAMED, Boolean.TRUE.toString());
            } else if ("ordinal".equalsIgnoreCase(enumType)) {
                simpleValue.setTypeName(ENUM_TYPE_CLASS);
                enumProperties.put(EnumType.TYPE, String.valueOf(Types.INTEGER));
                enumProperties.put(EnumType.NAMED, Boolean.FALSE.toString());
            } else if ("identity".equals(enumType)) {
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