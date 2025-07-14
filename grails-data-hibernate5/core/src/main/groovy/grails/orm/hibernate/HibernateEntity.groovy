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
import org.grails.orm.hibernate.AbstractHibernateGormStaticApi

/**
 * Extends the {@link GormEntity} trait adding additional Hibernate specific methods
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait HibernateEntity<D> extends GormEntity<D> {

    /**
     * Finds all objects for the given string-based query
     *
     * @param sql The query
     *
     * @return The object
     */
    @Generated
    static List<D> findAllWithSql(CharSequence sql) {
        currentHibernateStaticApi().findAllWithSql sql, Collections.emptyMap()
    }

    /**
     * Finds an entity for the given SQL query
     *
     * @param sql The sql query
     * @return The entity
     */
    @Generated
    static D findWithSql(CharSequence sql) {
        currentHibernateStaticApi().findWithSql(sql, Collections.emptyMap())
    }

    /**
     * Finds all objects for the given string-based query
     *
     * @param sql The query
     *
     * @return The object
     */
    @Generated
    static List<D> findAllWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findAllWithSql sql, args
    }

    /**
     * Finds an entity for the given SQL query
     *
     * @param sql The sql query
     * @return The entity
     */
    @Generated
    static D findWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findWithSql(sql, args)
    }

    @Generated
    private static AbstractHibernateGormStaticApi currentHibernateStaticApi() {
        (AbstractHibernateGormStaticApi) GormEnhancer.findStaticApi(this)
    }
}