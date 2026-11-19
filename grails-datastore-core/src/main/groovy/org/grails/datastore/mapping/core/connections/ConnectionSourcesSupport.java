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

package org.grails.datastore.mapping.core.connections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.util.ClassUtils;

import org.grails.datastore.mapping.config.Entity;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.EmbeddedCollection;
import org.grails.datastore.mapping.multitenancy.TenantDataSourceConfig;

/**
 * Utility methods for {@link ConnectionSource} handling
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class ConnectionSourcesSupport {

    public static final List<String> DEFAULT_CONNECTION_SOURCE_NAMES = Collections.singletonList(ConnectionSource.DEFAULT);

    private static final String JPA_ENTITY_ANNOTATION = "jakarta.persistence.Entity";

    /**
     * If a domain class uses more than one datasource, we need to know which one to use
     * when calling a method without a namespace qualifier.
     *
     * @param entity the domain class
     * @return the default datasource name
     */
    public static String getDefaultConnectionSourceName(PersistentEntity entity) {
        List<String> names = getConnectionSourceNames(entity);
        if (names.size() == 1 && ConnectionSource.ALL.equals(names.get(0))) {
            return ConnectionSource.ALL;
        }
        return names.get(0);
    }

    /**
     * Obtain all of the {@link ConnectionSource} names for the given entity
     *
     * @param entity The entity
     * @return The {@link ConnectionSource} names
     */
    public static List<String> getConnectionSourceNames(PersistentEntity entity) {
        final Entity mappedForm = entity.getMapping().getMappedForm();
        if (mappedForm != null) {
            return mappedForm.getDatasources();
        }
        return DEFAULT_CONNECTION_SOURCE_NAMES;
    }

    /**
     * Returns whether the given entity uses the give connection source name or not
     *
     * @param entity The name of the entity
     * @param connectionSourceName The connection source name
     * @return Whether the given connection source is used
     */
    public static boolean usesConnectionSource(PersistentEntity entity, String connectionSourceName) {
        Class[] interfaces = ClassUtils.getAllInterfacesForClass(entity.getJavaClass());
        if (isMultiTenant(interfaces)) {
            return !isMultiTenantExcludedDataSource(entity, connectionSourceName);
        }
        else {
            List<String> names = getConnectionSourceNames(entity);
            return names.contains(connectionSourceName) ||
                    names.contains(ConnectionSource.ALL);
        }
    }

    /**
     * With schema based multi-tenancy every tenant schema gets its own connection source, which maps the
     * multi tenant entities but only those other entities that use that connection source. An association
     * from a multi tenant entity to any other entity therefore has nothing to refer to in a tenant schema
     * that does not map the target.
     *
     * <p>Without the names of the tenant schemas the target has to use every connection source ({@code ALL}),
     * because any schema can be added later.
     *
     * @param entities The persistent entities of the application
     * @return A description of each association of a multi tenant entity to an entity the tenant schemas do
     * not map, in the form {@code Owner.property -> Target}; empty when there is none
     */
    public static List<String> findAssociationsToEntitiesMissingFromTenantSchemas(Iterable<? extends PersistentEntity> entities) {
        return findAssociationsToEntitiesMissingFromTenantSchemas(entities, Collections.<String>emptyList());
    }

    /**
     * The same check against the tenant schemas that are known: a target is mapped when it uses every connection
     * source or lists each of the named tenant schemas, so a shared entity that names the default connection and
     * the tenant schemas explicitly is accepted.
     *
     * @param entities The persistent entities of the application
     * @param tenantSchemaNames The names of the tenant schemas (connection sources) that exist now
     * @return A description of each association of a multi tenant entity to an entity that at least one tenant
     * schema does not map, in the form {@code Owner.property -> Target}; empty when there is none
     */
    public static List<String> findAssociationsToEntitiesMissingFromTenantSchemas(Iterable<? extends PersistentEntity> entities, Collection<String> tenantSchemaNames) {
        List<String> found = new ArrayList<>();
        for (PersistentEntity entity : entities) {
            if (!entity.isMultiTenant()) {
                continue;
            }
            for (PersistentProperty<?> property : entity.getPersistentProperties()) {
                if (!(property instanceof Association) || property instanceof Embedded || property instanceof EmbeddedCollection) {
                    continue;
                }
                PersistentEntity target = ((Association<?>) property).getAssociatedEntity();
                if (target != null && !target.isMultiTenant() && !isMappedWhereOwnerIs(entity, target, tenantSchemaNames)) {
                    found.add(entity.getJavaClass().getSimpleName() + '.' + property.getName() + " -> " + target.getJavaClass().getSimpleName());
                }
            }
        }
        return found;
    }

    /**
     * Whether the target is mapped on every connection source the owner is mapped on: each tenant schema, and the default
     * connection, which also maps a multi tenant entity. A target that lists the tenant schema but not the default connection
     * leaves the default session factory with an association to an entity it does not map.
     */
    private static boolean isMappedWhereOwnerIs(PersistentEntity owner, PersistentEntity target, Collection<String> tenantSchemaNames) {
        if (getConnectionSourceNames(target).contains(ConnectionSource.ALL) || isMappedByJpaAnnotation(target)) {
            return true;
        }
        if (tenantSchemaNames == null || tenantSchemaNames.isEmpty()) {
            return false;
        }
        Set<String> names = new LinkedHashSet<>(tenantSchemaNames);
        names.add(ConnectionSource.DEFAULT);
        for (String name : names) {
            if (usesConnectionSource(owner, name) && !usesConnectionSource(target, name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the entity is a JPA entity by its annotation. The Hibernate binders add such a class to every session factory,
     * whatever datasources its mapping lists, so it is mapped on each tenant schema.
     */
    private static boolean isMappedByJpaAnnotation(PersistentEntity entity) {
        for (java.lang.annotation.Annotation annotation : entity.getJavaClass().getAnnotations()) {
            if (JPA_ENTITY_ANNOTATION.equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isMultiTenant(Class[] interfaces) {
        for (Class anInterface : interfaces) {
            String name = anInterface.getName();
            if (name.startsWith("grails.gorm") && name.endsWith(".MultiTenant")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the given entity should be excluded from given connection source name or not.
     * It can be configured over {@link TenantDataSourceConfig} annotation
     *
     * @param entity The name of the multi tenant entity
     * @param connectionSourceName The connection source name to check
     * @return Whether the given connection should be excluded
     */
    protected static boolean isMultiTenantExcludedDataSource(PersistentEntity entity, String connectionSourceName) {
        TenantDataSourceConfig tdsc = (TenantDataSourceConfig) entity.getJavaClass().getAnnotation(TenantDataSourceConfig.class);
        boolean result = false;
        if (null != tdsc) {
            final String[] dataSourcesToExclude = tdsc.dataSourcesToExclude();
            result = Arrays.asList(dataSourcesToExclude).contains(connectionSourceName);
        }
        return result;
    }
}
