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
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
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
     * Finds all objects for the given native SQL query. The query must be a Groovy GString
     * so that interpolated values are safely bound as named parameters.
     *
     * @param sql The native SQL query (must be a GString with {@code ${value}} interpolations)
     * @return The matching objects
     */
    @Generated
    static List<D> findAllWithNativeSql(CharSequence sql) {
        currentHibernateStaticApi().findAllWithNativeSql sql, Collections.emptyMap()
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
        currentHibernateStaticApi().findAllWithNativeSql sql, args
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
     * @deprecated Use {@link #findAllWithNativeSql(CharSequence)} — the new name makes the native SQL risk surface explicit.
     */
    @Deprecated
    @Generated
    static List<D> findAllWithSql(CharSequence sql) {
        currentHibernateStaticApi().findAllWithNativeSql sql, Collections.emptyMap()
    }

    /**
     * @deprecated Use {@link #findWithNativeSql(CharSequence)} — the new name makes the native SQL risk surface explicit.
     */
    @Deprecated
    @Generated
    static D findWithSql(CharSequence sql) {
        currentHibernateStaticApi().findWithNativeSql(sql, Collections.emptyMap())
    }

    /**
     * @deprecated Use {@link #findAllWithNativeSql(CharSequence, Map)} — the new name makes the native SQL risk surface explicit.
     */
    @Deprecated
    @Generated
    static List<D> findAllWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findAllWithNativeSql sql, args
    }

    /**
     * @deprecated Use {@link #findWithNativeSql(CharSequence, Map)} — the new name makes the native SQL risk surface explicit.
     */
    @Deprecated
    @Generated
    static D findWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findWithNativeSql(sql, args)
    }

    @Generated
    private static HibernateGormStaticApi currentHibernateStaticApi() {
        (HibernateGormStaticApi)GormEnhancer.findStaticApi(this)
    }
}
