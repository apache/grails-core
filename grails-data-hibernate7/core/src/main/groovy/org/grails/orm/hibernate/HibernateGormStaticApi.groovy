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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.AbstractGormApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.gorm.DatastoreResolver
import org.hibernate.Session
import org.hibernate.query.NativeQuery

/**
 * Hibernate GORM static API.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected GrailsHibernateTemplate hibernateTemplate

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders, DatastoreResolver datastoreResolver, String qualifier, ClassLoader classLoader) {
        super(persistentClass, datastore.mappingContext, finders, datastoreResolver, qualifier)
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
    }

    HibernateGormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver datastoreResolver, String qualifier, ClassLoader classLoader) {
        super(persistentClass, mappingContext, finders, datastoreResolver, qualifier)
    }

    protected GrailsHibernateTemplate getHibernateTemplate() {
        if (this.hibernateTemplate == null) {
            HibernateDatastore datastore = (HibernateDatastore) getDatastore()
            return new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
        }
        return hibernateTemplate
    }

    @Override
    def <T> T withNewSession(Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        hibernateDatastore.withNewSession(callable)
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        hibernateDatastore.withSession(callable)
    }

    @Override
    def <T> T withNewSession(Serializable tenantId, Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        hibernateDatastore.withNewSession(tenantId, callable)
    }

    /**
     * Finds all results for this entity for the given SQL query
     *
     * @param sql The SQL query
     * @param args The arguments
     * @return All entities matching the SQL query
     */
    @CompileDynamic
    List<D> findAllWithNativeSql(CharSequence sql, Map args = Collections.emptyMap()) {
        return (List<D>) hibernateTemplate.execute { Session session ->

            List params = []
            if (sql instanceof GString) {
                sql = buildOrdinalParameterQueryFromGString((GString)sql, params)
            }

            NativeQuery q = (NativeQuery)session.createNativeQuery(sql.toString(), persistentClass)

            hibernateTemplate.applySettings(q)

            params.eachWithIndex { val, int i ->
                i++
                if (val instanceof CharSequence) {
                    q.setParameter(i, val.toString())
                }
                else {
                    q.setParameter(i, val)
                }
            }
            populateQueryArguments(q, args)
            return q.list()
        }
    }

    /**
     * Finds a single result for this entity for the given SQL query
     *
     * @param sql The SQL query
     * @param args The arguments
     * @return The entity matching the SQL query
     */
    @CompileDynamic
    D findWithNativeSql(CharSequence sql, Map args = Collections.emptyMap()) {
        return (D) hibernateTemplate.execute { Session session ->

            List params = []
            if (sql instanceof GString) {
                sql = buildOrdinalParameterQueryFromGString((GString)sql, params)
            }

            NativeQuery q = (NativeQuery)session.createNativeQuery(sql.toString(), persistentClass)

            hibernateTemplate.applySettings(q)

            params.eachWithIndex { val, int i ->
                i++
                if (val instanceof CharSequence) {
                    q.setParameter(i, val.toString())
                }
                else {
                    q.setParameter(i, val)
                }
            }
            populateQueryArguments(q, args)
            q.setMaxResults(1)
            return q.uniqueResult()
        }
    }

    protected String buildOrdinalParameterQueryFromGString(GString query, List params) {
        StringBuilder sqlString = new StringBuilder()
        int i = 0
        Object[] values = query.values
        def strings = query.getStrings()
        for (str in strings) {
            sqlString.append(str)
            if (i < values.length) {
                sqlString.append('?')
                params.add(values[i++])
            }
        }
        return sqlString.toString()
    }
}
