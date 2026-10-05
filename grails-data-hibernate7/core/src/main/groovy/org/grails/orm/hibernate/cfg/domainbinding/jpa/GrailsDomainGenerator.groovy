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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import java.lang.annotation.Annotation
import java.lang.reflect.Method

import groovy.transform.CompileStatic
import jakarta.persistence.Column as JpaColumn
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.hibernate.mapping.Column

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateVersionProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher

/**
 * Describes a GORM domain class to Hibernate as an annotated JPA entity.
 *
 * <p>GORM domain classes carry no JPA metadata, so Hibernate's annotation binder cannot read them. This class
 * turns an already-built {@link GrailsHibernatePersistentEntity} (its properties and its evaluated
 * {@code mapping} and {@code constraints}) into a generated class whose fields carry the annotations Hibernate
 * understands. The field set is exactly the entity's persistent properties, so Hibernate never reflects over the
 * Groovy class and sees none of its injected members.</p>
 *
 * <p>Column facets (length, precision, scale, column definition, uniqueness) are not derived a second time: the
 * same constraint binders the domain binder uses are run on a scratch {@link Column} and the result is read back,
 * so there is one set of rules.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsDomainGenerator {

    static final String GENERATED_PACKAGE = 'org.grails.orm.hibernate.generated'

    private final PersistentEntityNamingStrategy namingStrategy
    private final ColumnNameForPropertyAndPathFetcher columnNames
    private final ColumnConfigToColumnBinder columnConfigBinder
    private final StringColumnConstraintsBinder stringConstraints
    private final NumericColumnConstraintsBinder numericConstraints

    GrailsDomainGenerator(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnNameForPropertyAndPathFetcher columnNames,
            ColumnConfigToColumnBinder columnConfigBinder,
            StringColumnConstraintsBinder stringConstraints,
            NumericColumnConstraintsBinder numericConstraints) {
        this.namingStrategy = namingStrategy
        this.columnNames = columnNames
        this.columnConfigBinder = columnConfigBinder
        this.stringConstraints = stringConstraints
        this.numericConstraints = numericConstraints
    }

    /**
     * @param entity the entity to describe
     * @param parent the class loader the generated class is loaded under; the class lives as long as it does
     * @return a new class whose annotations describe the entity
     */
    Class<?> generate(GrailsHibernatePersistentEntity entity, ClassLoader parent) {
        if (!entity.isRoot()) {
            throw new UnsupportedOperationException(
                    "Entity [${entity.name}] is part of an inheritance hierarchy, which the generator does not support yet")
        }
        DynamicType.Builder<Object> builder = new ByteBuddy()
                .subclass(Object)
                .name(generatedClassName(entity))
                .annotateType(classAnnotations(entity) as AnnotationDescription[])

        HibernatePersistentProperty identity = (HibernatePersistentProperty) entity.identity
        if (identity != null) {
            builder = defineField(builder, identity, [AnnotationDescription.Builder.ofType(Id).build()])
        }
        for (HibernatePersistentProperty property : entity.persistentPropertiesToBind) {
            if (!supports(property)) {
                throw new UnsupportedOperationException(
                        "Property [${property.name}] of [${entity.name}] is a ${property.getClass().simpleName}, " +
                                'which the generator does not support yet')
            }
            builder = defineField(builder, property, [])
        }
        return builder.make().load(parent, ClassLoadingStrategy.Default.WRAPPER).loaded
    }

    static String generatedClassName(GrailsHibernatePersistentEntity entity) {
        return GENERATED_PACKAGE + '.' + entity.javaClass.name.replace('.', '_')
    }

    private List<AnnotationDescription> classAnnotations(GrailsHibernatePersistentEntity entity) {
        Mapping mapping = entity.mappedForm
        List<AnnotationDescription> annotations = []
        boolean autoImport = mapping == null || mapping.autoImport
        annotations << AnnotationDescription.Builder.ofType(Entity)
                .define('name', autoImport ? entity.javaClass.simpleName : entity.javaClass.name)
                .build()

        AnnotationDescription.Builder table = AnnotationDescription.Builder.ofType(Table)
                .define('name', entity.getTableName(namingStrategy))
        if (mapping?.table?.schema) {
            table = table.define('schema', mapping.table.schema)
        }
        if (mapping?.table?.catalog) {
            table = table.define('catalog', mapping.table.catalog)
        }
        annotations << table.build()
        return annotations
    }

    private DynamicType.Builder<Object> defineField(
            DynamicType.Builder<Object> builder, HibernatePersistentProperty property, List<AnnotationDescription> extra) {
        List<AnnotationDescription> annotations = new ArrayList<>(extra)
        annotations << columnAnnotation(property)
        for (Annotation constraint : validationAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        return builder.defineField(property.name, property.type, Visibility.PRIVATE).annotateField(annotations as AnnotationDescription[])
    }

    /**
     * @return whether the generator can describe the property today: a plain single-column basic property, or the
     *     simple identifier. Enums, versions, custom types, derived properties, multi-column properties and every
     *     association are not supported yet.
     */
    boolean supports(HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return property.hibernateOwner.isRoot()
        }
        if (!(property instanceof HibernateSimpleProperty) ||
                property instanceof HibernateEnumProperty ||
                property instanceof HibernateVersionProperty) {
            return false
        }
        PropertyConfig mappedForm = property.hibernateMappedForm
        return !mappedForm.derived && (mappedForm.columns == null || mappedForm.columns.size() <= 1)
    }

    /**
     * Decides the column facets for a supported property by running the domain binder's own constraint rules on a
     * scratch {@link Column}, in the order the binder applies them.
     */
    ColumnFacets columnFacets(HibernatePersistentProperty property) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        List<ColumnConfig> columns = mappedForm.columns
        ColumnConfig columnConfig = columns == null || columns.isEmpty() ? null : columns[0]

        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm)
        String name = columnNames.getColumnNameForPropertyAndPath(property, null, columnConfig)
        Class<?> type = property.type
        if (type != null && (String.isAssignableFrom(type) || byte[].isAssignableFrom(type))) {
            stringConstraints.bindStringColumnConstraints(column, mappedForm, property.typeName)
        } else if (type != null && Number.isAssignableFrom(type)) {
            numericConstraints.bindNumericColumnConstraints(column, columnConfig, mappedForm, type)
        }
        return new ColumnFacets(
                name,
                isNullable(property),
                mappedForm.isUnique() && !mappedForm.isUniqueWithinGroup(),
                mappedForm.insertable,
                mappedForm.updatable,
                column.length?.intValue(),
                column.precision?.intValue(),
                column.scale?.intValue(),
                column.sqlType)
    }

    private AnnotationDescription columnAnnotation(HibernatePersistentProperty property) {
        ColumnFacets facets = columnFacets(property)
        AnnotationDescription.Builder annotation = AnnotationDescription.Builder.ofType(JpaColumn)
                .define('name', facets.name())
                .define('nullable', facets.nullable())
                .define('unique', facets.unique())
                .define('insertable', facets.insertable())
                .define('updatable', facets.updatable())
        if (facets.length() != null) {
            annotation = annotation.define('length', facets.length())
        }
        if (facets.precision() != null) {
            annotation = annotation.define('precision', facets.precision())
        }
        if (facets.scale() != null) {
            annotation = annotation.define('scale', facets.scale())
        }
        if (facets.sqlType()) {
            annotation = annotation.define('columnDefinition', facets.sqlType())
        }
        return annotation.build()
    }

    /**
     * The Bean Validation constraints declared on the property. Hibernate turns them into DDL (not null, precision,
     * scale, length) after binding, so they are copied onto the generated field for Hibernate to apply itself.
     */
    List<Annotation> validationAnnotations(HibernatePersistentProperty property) {
        Class<?> owner = property.hibernateOwner.javaClass
        List<Annotation> found = []
        try {
            found.addAll(owner.getDeclaredField(property.name).declaredAnnotations as List<Annotation>)
        } catch (NoSuchFieldException ignored) {
            // the property has no backing field of its own
        }
        String accessor = 'get' + property.name.capitalize()
        for (Method method : owner.declaredMethods) {
            if (method.name == accessor && method.parameterCount == 0) {
                found.addAll(method.declaredAnnotations as List<Annotation>)
            }
        }
        return found.findAll { Annotation a ->
            String type = a.annotationType().name
            type.startsWith('jakarta.validation.constraints.') || type.startsWith('org.hibernate.validator.constraints.')
        }
    }

    private static boolean isNullable(HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return false
        }
        if (!property.hibernateOwner.isRoot()) {
            Mapping mapping = property.hibernateOwner.hibernateMappedForm
            return mapping != null && mapping.tablePerHierarchy ? true : property.nullable
        }
        return property.nullable
    }
}
