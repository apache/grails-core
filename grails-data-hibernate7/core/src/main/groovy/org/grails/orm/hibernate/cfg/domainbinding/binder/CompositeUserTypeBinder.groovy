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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import java.lang.reflect.Type

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.property.access.internal.PropertyAccessStrategyCompositeUserTypeImpl
import org.hibernate.property.access.spi.PropertyAccessStrategy
import org.hibernate.usertype.CompositeUserType
import org.jspecify.annotations.NonNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CompositeUserTypeColumns
import org.grails.orm.hibernate.cfg.domainbinding.util.CompositeUserTypeColumns.Attribute
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Binds a property mapped with a Hibernate {@link CompositeUserType} as a {@link Component}.
 * <p>
 * Hibernate resolves the type of a {@link BasicValue} as a {@link org.hibernate.type.BasicType} or a
 * {@link org.hibernate.usertype.UserType} only. A composite user type spans one column per attribute of its
 * {@linkplain CompositeUserType#embeddable() embeddable class}, so it is a component whose type name is the
 * user type, as Hibernate binds a {@link org.hibernate.annotations.CompositeType}: one property per attribute,
 * in alphabetical attribute order, read through {@link CompositeUserType#getPropertyValue(Object, int)} with the
 * index of the attribute in that order and assembled through
 * {@link CompositeUserType#instantiate(org.hibernate.metamodel.spi.ValueAccess)}. The columns are the ones
 * {@link CompositeUserTypeColumns} assigns to the attributes: the columns of the property mapping in the same
 * order, and a name made of the property and the attribute for an attribute without a configured column. The
 * component class is the {@linkplain CompositeUserType#returnedClass() value class} of the user type, not its
 * embeddable class. A unique property has one unique key across all of its columns, together with the columns
 * of its uniqueness group, rather than a unique key per column. The constraints of the property describe the
 * whole value, so each column is defined from its own configuration and the type of its attribute: a numeric
 * attribute without a configured precision gets the default precision of the dialect, as a numeric property
 * without constraints does. The value class may be an enum. The user type is instantiated through
 * {@link CompositeUserTypeColumns#userTypeOf}, as it is for a uniqueness group that names the property: as a
 * managed bean when the building options allow extensions in CDI, through its default constructor otherwise.
 *
 * @since 8.0.1
 */
@CompileStatic
class CompositeUserTypeBinder {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeUserTypeBinder)

    private final MetadataBuildingContext metadataBuildingContext
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder
    private final ColumnBinder columnBinder
    private final CreateKeyForProps createKeyForProps
    private final CompositeUserTypeColumns compositeUserTypeColumns

    CompositeUserTypeBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this(
                metadataBuildingContext,
                new ColumnConfigToColumnBinder(),
                new ColumnBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment.getDialect()),
                new CreateKeyForProps(new ColumnNameForPropertyAndPathFetcher(
                        namingStrategy, new DefaultColumnNameFetcher(namingStrategy), new BackticksRemover(),
                        metadataBuildingContext)),
                new CompositeUserTypeColumns(namingStrategy, new BackticksRemover(), metadataBuildingContext))
    }

    protected CompositeUserTypeBinder(
            MetadataBuildingContext metadataBuildingContext,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            ColumnBinder columnBinder,
            CreateKeyForProps createKeyForProps,
            CompositeUserTypeColumns compositeUserTypeColumns) {
        this.metadataBuildingContext = metadataBuildingContext
        this.columnConfigToColumnBinder = columnConfigToColumnBinder
        this.columnBinder = columnBinder
        this.createKeyForProps = createKeyForProps
        this.compositeUserTypeColumns = compositeUserTypeColumns
    }

    /**
     * Binds the property as a component of its composite user type.
     *
     * @param property the property, mapped with a composite user type
     * @param parentProperty the embedded property that holds the property, or {@code null}
     * @param path the path of the property within its entity
     * @return the component
     * @throws org.hibernate.MappingException when the property is not mapped with a composite user type, or maps
     * more columns than its embeddable class has attributes
     */
    Component bindCompositeUserType(
            @NonNull HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            String path) {
        PersistentClass owner = property.getPersistentClass()
        CompositeUserType<?> userType = compositeUserTypeColumns.userTypeOf(property)
        Class<? extends CompositeUserType<?>> userTypeClass = property.getCompositeUserType()
        Class<?> embeddable = userType.embeddable()
        PropertyConfig propertyConfig = property.getHibernateMappedForm()
        Table table = property.getTable()

        Component component = new Component(metadataBuildingContext, owner)
        component.setRoleName(GrailsHibernateUtil.qualify(
                property.getHibernateOwner().getJavaClass().getName(), property.getNameForPropertyAndPath(path)))
        component.setTypeName(userTypeClass.getName())
        // The component describes the value class, as Hibernate's own binding of a composite type does: the
        // embeddable class only supplies the attributes, so whole-value queries and parameters see the value class.
        component.setComponentClassName(userType.returnedClass().getName())

        Properties typeParams = propertyConfig.getTypeParams()
        if (typeParams != null && !typeParams.isEmpty()) {
            LOG.warn('Property [{}] of class [{}] sets typeParams, which Hibernate does not pass to the composite ' +
                    'user type [{}]', property.getName(), owner.getClassName(), userTypeClass.getName())
        }

        List<Attribute> attributes = CompositeUserTypeColumns.attributesOf(embeddable)
        List<ColumnConfig> columnConfigs =
                compositeUserTypeColumns.columnConfigsOf(property, path, embeddable, attributes)

        List<String> attributeNames = new ArrayList<>(attributes.size())
        List<Type> attributeTypes = new ArrayList<>(attributes.size())
        List<Column> columns = new ArrayList<>(attributes.size())
        PropertyAccessStrategy accessStrategy =
                new PropertyAccessStrategyCompositeUserTypeImpl(userType, attributeNames, attributeTypes)
        for (int i = 0; i < attributes.size(); i++) {
            Attribute attribute = attributes.get(i)
            ColumnConfig columnConfig = columnConfigs.get(i)

            BasicValue value = new BasicValue(metadataBuildingContext, table)
            value.setTypeUsingReflection(embeddable.getName(), attribute.name())

            Column column = new Column()
            columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, propertyConfig)
            // The column holds the attribute, so its constraints come from its own configuration and the type of
            // the attribute, not from the constraints of the property, which describe the whole value
            columnBinder.bindColumnWithoutUniqueness(property, parentProperty, column, columnConfig, path, table,
                    userTypeClass.getName(), attribute.rawType())
            if (table != null) {
                table.addColumn(column)
            }
            value.addColumn(column)
            columns.add(column)

            Property attributeProperty = new Property()
            attributeProperty.setName(attribute.name())
            attributeProperty.setValue(value)
            attributeProperty.setOptional(column.isNullable())
            attributeProperty.setPropertyAccessStrategy(accessStrategy)
            component.addProperty(attributeProperty)

            attributeNames.add(attribute.name())
            attributeTypes.add(attribute.type())
        }
        if (table != null) {
            // The bound columns themselves, so that the key keeps their quoting and case
            createKeyForProps.createKeyForColumns(property, path, table, columns)
        }
        component.sortProperties()
        return component
    }
}
