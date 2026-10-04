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

/**
 * A query implementation for HQL mutation queries (UPDATE/DELETE).
 *
 * @author Graeme Rocher
 * @since 7.0.0
 */
@CompileStatic
@SuppressWarnings('rawtypes')
class MutationHqlQuery extends Query implements HqlQueryMethods {

    private final HqlQueryContext queryContext
    // Named queryDelegate, not delegate: a bare `delegate` reference inside a closure resolves to
    // Closure.getDelegate() (which defaults to the closure's owner, i.e. this instance), not this field.
    private final HqlQueryDelegate queryDelegate

    protected MutationHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext, HqlQueryDelegate queryDelegate) {
        super(session, entity)
        this.queryContext = queryContext
        this.queryDelegate = queryDelegate
    }

    int executeUpdate() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) hibernateTemplate
        return template.execute({ Object __ ->
            applyQuerySettings(queryDelegate)
            return queryDelegate.executeUpdate()
        })
    }

    protected void applyQuerySettings(HqlQueryDelegate d) {
        populateQuerySettings(d, queryContext.querySettings())
        populateHints(d, queryContext.hints())
        HqlQueryMethods.populateParameters(d, queryContext)
    }

    @Override
    List list() {
        throw new UnsupportedOperationException('Mutation query (UPDATE/DELETE) cannot be used for list(); use executeUpdate() instead')
    }

    @Override
    Object singleResult() {
        throw new UnsupportedOperationException('Mutation query (UPDATE/DELETE) cannot be used for singleResult(); use executeUpdate() instead')
    }

    private IHibernateTemplate getHibernateTemplate() {
        HibernateSession hibernateSession = (HibernateSession) session
        return (IHibernateTemplate) hibernateSession.nativeInterface
    }

    @Override
    protected List executeQuery(org.grails.datastore.mapping.model.PersistentEntity entity, Junction criteria) {
        throw new UnsupportedOperationException('Mutation query (UPDATE/DELETE) cannot be used for executeQuery(); use executeUpdate() instead')
    }

    org.hibernate.query.Query selectQuery() {
        return null
    }

}
