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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.usertype.CompositeUserType;

import org.springframework.beans.BeanUtils;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

/**
 * The columns of a property mapped with a Hibernate {@link CompositeUserType}: one per persistent attribute of
 * the {@linkplain CompositeUserType#embeddable() embeddable class} of the user type, in the alphabetical order of
 * the attribute names, which is the index that {@link CompositeUserType#getPropertyValue(Object, int)} and
 * {@link CompositeUserType#instantiate(org.hibernate.metamodel.spi.ValueAccess)} use. The columns configured on
 * the property are assigned to the attributes in that order, and an attribute without a configured column, or
 * whose column has no name, is named after the property and the attribute. The binder of such a property binds
 * these columns, and a uniqueness group that names the property spans all of them.
 * <p>
 * The user type is {@linkplain #userTypeOf instantiated here}, for the binder of the property and for the
 * uniqueness groups that name the property alike, the way Hibernate instantiates it when it resolves the type of
 * a component: as a managed bean when the building options allow extensions in CDI, through its default
 * constructor otherwise.
 *
 * @since 8.0.1
 */
public class CompositeUserTypeColumns {

    private static final String UNDERSCORE = "_";

    private final PersistentEntityNamingStrategy namingStrategy;
    private final BackticksRemover backticksRemover;
    private final MetadataBuildingContext metadataBuildingContext;

    /**
     * Creates the columns of a binding that has no {@link MetadataBuildingContext}: a composite user type is then
     * instantiated through its default constructor, as Hibernate does outside a CDI container.
     *
     * @param namingStrategy the naming strategy of the default column names
     * @param backticksRemover the remover of the backticks of a quoted name
     * @see #CompositeUserTypeColumns(PersistentEntityNamingStrategy, BackticksRemover, MetadataBuildingContext)
     */
    public CompositeUserTypeColumns(PersistentEntityNamingStrategy namingStrategy, BackticksRemover backticksRemover) {
        this(namingStrategy, backticksRemover, null);
    }

    /**
     * @param namingStrategy the naming strategy of the default column names
     * @param backticksRemover the remover of the backticks of a quoted name
     * @param metadataBuildingContext the context of the binding, whose options decide whether a composite user
     * type is a managed bean of the {@linkplain org.hibernate.boot.spi.BootstrapContext#getManagedBeanRegistry()
     * bean registry}; {@code null} instantiates every user type through its default constructor
     */
    public CompositeUserTypeColumns(
            PersistentEntityNamingStrategy namingStrategy,
            BackticksRemover backticksRemover,
            MetadataBuildingContext metadataBuildingContext) {
        this.namingStrategy = namingStrategy;
        this.backticksRemover = backticksRemover;
        this.metadataBuildingContext = metadataBuildingContext;
    }

    /**
     * The persistent attributes of the embeddable class, in alphabetical order: the components of a record, or
     * the fields and the readable bean properties of any other class, less the static, transient and synthetic
     * ones, the ones every Groovy object has, the ones the class lists in {@code transients} and the ones backed
     * by a transient field. A setter is not required, nor is an accessor at all: Hibernate never writes the
     * embeddable class, it reads the attributes through {@link CompositeUserType#getPropertyValue(Object, int)}.
     * The type of an attribute is the type of its field, or of its getter when it has no field, which is how
     * Hibernate resolves it.
     *
     * @param embeddable the embeddable class of the user type
     * @return the attributes, in alphabetical order of their names
     */
    public static List<Attribute> attributesOf(Class<?> embeddable) {
        if (embeddable.isRecord()) {
            return Arrays.stream(embeddable.getRecordComponents())
                    .map(component -> new Attribute(component.getName(), component.getGenericType()))
                    .sorted(Comparator.comparing(Attribute::name))
                    .toList();
        }
        Map<String, Type> attributes = new TreeMap<>();
        ReflectionUtils.doWithFields(embeddable,
                field -> attributes.putIfAbsent(field.getName(), field.getGenericType()),
                field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()) &&
                        !Modifier.isTransient(field.getModifiers()));
        for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(embeddable)) {
            Method getter = descriptor.getReadMethod();
            if (getter != null && !Modifier.isStatic(getter.getModifiers())) {
                attributes.putIfAbsent(descriptor.getName(), getter.getGenericReturnType());
            }
        }
        Collection<?> transients = Optional.ofNullable(
                ClassPropertyFetcher.getStaticPropertyValue(embeddable, GormProperties.TRANSIENT, Collection.class))
                .orElseGet(List::of);
        attributes.keySet().removeIf(name -> ClassPropertyFetcher.EXCLUDED_PROPERTIES.contains(name) ||
                transients.contains(name) || hasTransientField(embeddable, name));
        return attributes.entrySet().stream()
                .map(entry -> new Attribute(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * The composite user type of the property, instantiated the way Hibernate instantiates it when it resolves
     * the type of a component: as a managed bean of the bean registry when the building options allow extensions
     * in CDI, through its default constructor otherwise. The binder of the property and the uniqueness groups
     * that name the property both resolve the user type here, so a type that only a bean container can create
     * is created the same way wherever the binding needs it.
     *
     * @param property the property, mapped with a composite user type
     * @return an instance of the user type of the property
     * @throws MappingException when the property is not mapped with a composite user type
     * @throws org.springframework.beans.BeanInstantiationException when the user type has no default constructor
     * and no bean container creates it
     */
    public CompositeUserType<?> userTypeOf(HibernatePersistentProperty property) {
        Class<? extends CompositeUserType<?>> userTypeClass = property.getCompositeUserType();
        if (userTypeClass == null) {
            throw new MappingException("Property [" + property.getName() + "] of class [" +
                    property.getOwner().getName() + "] is not mapped with a " +
                    CompositeUserType.class.getSimpleName());
        }
        if (metadataBuildingContext != null && metadataBuildingContext.getBuildingOptions().isAllowExtensionsInCdi()) {
            return metadataBuildingContext.getBootstrapContext()
                    .getManagedBeanRegistry()
                    .getBean(userTypeClass)
                    .getBeanInstance();
        }
        return BeanUtils.instantiateClass(userTypeClass);
    }

    /**
     * The column configuration of each attribute of the property, in attribute order: the configured column when
     * it names its column, otherwise a copy of it, or a new configuration, named after the property and the
     * attribute.
     *
     * @param property the property, mapped with a composite user type
     * @param path the path of the property within its entity
     * @param embeddable the embeddable class of the user type
     * @param attributes the attributes of the embeddable class, as {@link #attributesOf} lists them
     * @return one named column configuration per attribute
     * @throws MappingException when the property maps more columns than the embeddable class has attributes
     */
    public List<ColumnConfig> columnConfigsOf(
            HibernatePersistentProperty property, String path, Class<?> embeddable, List<Attribute> attributes) {
        List<ColumnConfig> columns = Optional.ofNullable(property.getHibernateMappedForm())
                .map(PropertyConfig::getColumns)
                .orElseGet(List::of);
        if (columns.size() > attributes.size()) {
            throw new MappingException(String.format(
                    "Property [%s] of class [%s] maps %d columns, but the embeddable class [%s] of its composite " +
                            "user type [%s] has %d attributes: %s",
                    property.getName(), property.getOwner().getName(), columns.size(), embeddable.getName(),
                    property.getCompositeUserType().getName(), attributes.size(),
                    attributes.stream().map(Attribute::name).toList()));
        }
        List<ColumnConfig> named = new ArrayList<>(attributes.size());
        for (int i = 0; i < attributes.size(); i++) {
            named.add(withName(i < columns.size() ? columns.get(i) : null,
                    defaultColumnName(property, path, attributes.get(i).name())));
        }
        return named;
    }

    /**
     * The names of the columns of the property, in attribute order, as {@link #columnConfigsOf} names them, from
     * the embeddable class of the {@linkplain #userTypeOf user type of the property}.
     *
     * @param property the property, mapped with a composite user type
     * @param path the path of the property within its entity
     * @return the column names
     * @throws MappingException when the property is not mapped with a composite user type, or maps more columns
     * than its embeddable class has attributes
     */
    public List<String> columnNamesOf(HibernatePersistentProperty property, String path) {
        Class<?> embeddable = userTypeOf(property).embeddable();
        return columnConfigsOf(property, path, embeddable, attributesOf(embeddable)).stream()
                .map(ColumnConfig::getName)
                .toList();
    }

    /** Whether the property is backed by a {@code transient} field, which makes it transient however it is read. */
    private static boolean hasTransientField(Class<?> embeddable, String name) {
        Field field = ReflectionUtils.findField(embeddable, name);
        return field != null && Modifier.isTransient(field.getModifiers());
    }

    private static ColumnConfig withName(ColumnConfig columnConfig, String defaultName) {
        if (columnConfig != null && columnConfig.getName() != null && !columnConfig.getName().isEmpty()) {
            return columnConfig;
        }
        ColumnConfig named = new ColumnConfig();
        if (columnConfig != null) {
            named.setSqlType(columnConfig.getSqlType());
            named.setEnumType(columnConfig.getEnumType());
            named.setIndex(columnConfig.getIndex());
            named.setUnique(columnConfig.getUnique());
            named.setLength(columnConfig.getLength());
            named.setPrecision(columnConfig.getPrecision());
            named.setScale(columnConfig.getScale());
            named.setDefaultValue(columnConfig.getDefaultValue());
            named.setComment(columnConfig.getComment());
            named.setRead(columnConfig.getRead());
            named.setWrite(columnConfig.getWrite());
        }
        named.setName(defaultName);
        return named;
    }

    /**
     * The default name of the column of an attribute: the column name of the property, prefixed with the path of
     * the property when it belongs to an embedded component, followed by the column name of the attribute.
     */
    private String defaultColumnName(HibernatePersistentProperty property, String path, String attribute) {
        String prefix = backticksRemover.apply(namingStrategy.resolveColumnName(property.getName()));
        if (GrailsHibernateUtil.isNotEmpty(path)) {
            prefix = backticksRemover.apply(namingStrategy.resolveColumnName(path)) + UNDERSCORE + prefix;
        }
        return prefix + UNDERSCORE + backticksRemover.apply(namingStrategy.resolveColumnName(attribute));
    }

    /** A persistent attribute of the embeddable class of a composite user type: its name and its Java type. */
    public record Attribute(String name, Type type) {

        /**
         * The class of the attribute: the type itself when it is a class, the raw class of a parameterized type,
         * and {@code Object} for a type that resolves to no class, such as a type variable.
         *
         * @return the class of the attribute
         */
        public Class<?> rawType() {
            return ResolvableType.forType(type).toClass();
        }
    }
}
