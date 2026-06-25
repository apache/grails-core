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
package grails.gorm.hibernate

import groovy.transform.CompileStatic
import groovy.transform.Generated
import org.codehaus.groovy.runtime.InvokerHelper

import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.HibernateGormStaticApi

/**
 * Extends the {@link GormEntity} trait adding additional Hibernate specific methods
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait HibernateEntity<D> extends GormEntity<D> {

    /**
     * Finds all objects for the given native SQL query. Pass a GString to have interpolated
     * values safely bound as named parameters rather than interpolated into the query string.
     *
     * @param sql The native SQL query
     * @return The matching objects
     */
    @Generated
    static List<D> findAllWithNativeSql(CharSequence sql) {
        currentHibernateStaticApi().findAllWithNativeSql(sql, Collections.emptyMap())
    }

    /**
     * Finds an entity for the given native SQL query. The query must be a Groovy GString
     * so that interpolated values are safely bound as named parameters.
     *
     * @param sql The native SQL query (must be a GString with {@code ${value}} interpolations)
     * @return The entity
     */
    @Generated
    static D findWithNativeSql(CharSequence sql) {
        currentHibernateStaticApi().findWithNativeSql(sql, Collections.emptyMap())
    }

    /**
     * Finds all objects for the given native SQL query. The query must be a Groovy GString
     * so that interpolated values are safely bound as named parameters.
     *
     * @param sql  The native SQL query (must be a GString with {@code ${value}} interpolations)
     * @param args Pagination/query settings (max, offset, cache, etc.) — NOT SQL parameters
     * @return The matching objects
     */
    @Generated
    static List<D> findAllWithNativeSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findAllWithNativeSql(sql, args)
    }

    /**
     * Finds an entity for the given native SQL query. The query must be a Groovy GString
     * so that interpolated values are safely bound as named parameters.
     *
     * @param sql  The native SQL query (must be a GString with {@code ${value}} interpolations)
     * @param args Pagination/query settings (max, offset, cache, etc.) — NOT SQL parameters
     * @return The entity
     */
    @Generated
    static D findWithNativeSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findWithNativeSql(sql, args)
    }

    /**
     * Finds all objects for the given SQL query. Pass a GString to have interpolated
     * values safely bound as named parameters rather than interpolated into the query string.
     *
     * @param sql The SQL query
     * @return The matching objects
     */
    @Generated
    static List<D> findAllWithSql(CharSequence sql) {
        currentHibernateStaticApi().findAllWithNativeSql(sql, Collections.emptyMap())
    }

    /**
     * Finds an entity for the given SQL query. Pass a GString to have interpolated
     * values safely bound as named parameters rather than interpolated into the query string.
     *
     * @param sql The SQL query
     * @return The entity
     */
    @Generated
    static D findWithSql(CharSequence sql) {
        currentHibernateStaticApi().findWithNativeSql(sql, Collections.emptyMap())
    }

    /**
     * Finds all objects for the given SQL query. Pass a GString to have interpolated
     * values safely bound as named parameters rather than interpolated into the query string.
     *
     * @param sql  The SQL query
     * @param args Pagination/query settings (max, offset, cache, etc.)
     * @return The matching objects
     */
    @Generated
    static List<D> findAllWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findAllWithNativeSql(sql, args)
    }

    /**
     * Finds an entity for the given SQL query. Pass a GString to have interpolated
     * values safely bound as named parameters rather than interpolated into the query string.
     *
     * @param sql  The SQL query
     * @param args Pagination/query settings (max, offset, cache, etc.)
     * @return The entity
     */
    @Generated
    static D findWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findWithNativeSql(sql, args)
    }

    @Generated
    private static HibernateGormStaticApi currentHibernateStaticApi() {
        (HibernateGormStaticApi) GormEnhancer.findStaticApi(this)
    }

    /**
     * Overrides {@link GormEntity#addTo} to fix "Found two representations of same collection"
     * in Hibernate 7.
     *
     * H7 uses bytecode-enhanced attribute interception: the entity field for a collection is
     * physically null until first accessed through the getter. {@link GormEntity#addTo} uses
     * direct field access via {@link EntityReflector}, so it sees null and creates a new plain
     * ArrayList — which collides with the PersistentBag already tracked in the session.
     *
     * The fix: when the entity is already persisted (has an id) and the field is null, access the
     * collection through the getter via {@link InvokerHelper}. H7's attribute interceptor then
     * returns the session-tracked PersistentBag. We write it back to the field so the base
     * {@code addTo} finds it and adds directly into the PersistentBag without creating a plain one.
     */
    @Generated
    D addTo(String associationName, Object arg) {
        if (ident() != null) {
            PersistentEntity pe = getGormPersistentEntity()
            def prop = pe.getPropertyByName(associationName)
            if (prop instanceof Association && !(prop instanceof ToOne)) {
                EntityReflector reflector = pe.mappingContext.getEntityReflector(pe)
                if (reflector != null && reflector.getProperty((D) this, associationName) == null) {
                    // Access through the getter — H7's attribute interceptor returns the PersistentBag
                    def persistentColl = InvokerHelper.getProperty(this, associationName)
                    if (persistentColl != null) {
                        reflector.setProperty((D) this, associationName, persistentColl)
                    }
                }
            }
        }
        return GormEntity.super.addTo(associationName, arg)
    }
}
