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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.GrailsHibernateTemplate
import org.grails.orm.hibernate.HibernateSession
import org.grails.orm.hibernate.IHibernateTemplate
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

@CompileStatic
class SelectHqlQuery extends Query implements HqlQueryMethods, Serializable {

    protected final transient HqlQueryContext queryContext
    // Named queryDelegate, not delegate: a bare `delegate` reference inside a closure resolves to
    // Closure.getDelegate() (which defaults to the closure's owner, i.e. this instance), not this field.
    protected final transient HqlQueryDelegate queryDelegate

    protected SelectHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext, HqlQueryDelegate queryDelegate) {
        super(session, entity)
        this.queryContext = queryContext
        this.queryDelegate = queryDelegate
    }

    @Override
    List<?> list() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) hibernateTemplate
        return template.execute({ Object __ ->
            applyQuerySettings(queryDelegate)
            return queryDelegate.list()
        })
    }

    @Override
    Object singleResult() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) hibernateTemplate
        return template.execute({ Object __ ->
            applyQuerySettings(queryDelegate)
            List<?> results = queryDelegate.list()
            return results.isEmpty() ? null : results.first
        })
    }

    protected void applyQuerySettings(HqlQueryDelegate d) {
        Integer m = this.@max
        if (m != null && m > -1) {
            d.setMaxResults(m)
        }
        Integer o = this.@offset
        if (o != null && o > -1) {
            d.setFirstResult(o)
        }
        populateQuerySettings(d, queryContext.querySettings())
        populateHints(d, queryContext.hints())
        HqlQueryMethods.populateParameters(d, queryContext)
    }

    int executeUpdate() {
        throw new UnsupportedOperationException('SELECT query cannot be used for executeUpdate(); use a MutationHqlQuery instead')
    }

    protected IHibernateTemplate getHibernateTemplate() {
        HibernateSession hibernateSession = (HibernateSession) session
        return (IHibernateTemplate) hibernateSession.nativeInterface
    }

    @Override
    protected List executeQuery(org.grails.datastore.mapping.model.PersistentEntity entity, Junction criteria) {
        return list()
    }

    void setReadOnly(Boolean ignoredReadOnly) {
        // Compatibility method
    }

    org.hibernate.query.Query<?> selectQuery() {
        return queryDelegate.selectQuery()
    }

    @Override
    Integer getMax() {
        Integer m = this.@max
        if (m != null && m > -1) {
            return m
        }
        Object value = queryContext.querySettings().get(HibernateQueryArgument.MAX.value())
        return value == null ? -1 : HqlQueryMethods.toInteger(value)
    }

    @Override
    Integer getOffset() {
        Integer o = this.@offset
        if (o != null && o > -1) {
            return o
        }
        Object value = queryContext.querySettings().get(HibernateQueryArgument.OFFSET.value())
        return value == null ? 0 : HqlQueryMethods.toInteger(value)
    }

}
