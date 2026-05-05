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
package org.grails.orm.hibernate.query;

import java.io.Serializable;
import java.util.List;

import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

public class SelectHqlQuery extends Query implements HqlQueryMethods, Serializable {
    protected final transient HqlQueryContext queryContext;
    protected final transient HqlQueryDelegate delegate;

    private boolean wrapping = false;
    private boolean countQuery = false;

    protected SelectHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext, HqlQueryDelegate delegate) {
        super(session, entity);
        this.queryContext = queryContext;
        this.delegate = delegate;
    }

    @Override
    public ProjectionList projections() {
        return new ProjectionList() {
            @Override
            public org.grails.datastore.mapping.query.api.ProjectionList count() {
                countQuery = true;
                return this;
            }
        };
    }

    @Override
    public List<?> list() {
        if (getMax() > 0 && !wrapping && !countQuery) {
            wrapping = true;
            try {
                return new PagedResultList(this);
            } finally {
                wrapping = false;
            }
        }
        return executeListInternal();
    }

    protected List<?> executeListInternal() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return template.execute(__ -> {
            if (countQuery) {
                HqlListQueryBuilder builder = new HqlListQueryBuilder((GrailsHibernatePersistentEntity) entity, queryContext.querySettings());
                String countHql = builder.buildCountHql();
                org.hibernate.query.Query<?> q = __.createQuery(countHql, Long.class);
                HqlQueryMethods.populateParameters(new SelectQueryDelegate(q), queryContext);
                return q.list();
            }
            applyQuerySettings(delegate);
            return delegate.list();
        });
    }

    @Override
    public SelectHqlQuery clone() {
        HibernateSession hibernateSession = (HibernateSession) getSession();
        SelectHqlQuery cloned = (SelectHqlQuery) HibernateHqlQueryCreator.createHqlQuery(
                (HibernateDatastore) hibernateSession.getDatastore(),
                hibernateSession.getHibernateTemplate().getSessionFactory(),
                entity,
                queryContext
        );
        if (this.max != null) {
            cloned.max(this.max);
        }
        if (this.offset != null) {
            cloned.offset(this.offset);
        }
        return cloned;
    }

    @Override
    public Object singleResult() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return template.execute(__ -> {
            if (countQuery) {
                HqlListQueryBuilder builder = new HqlListQueryBuilder((GrailsHibernatePersistentEntity) entity, queryContext.querySettings());
                String countHql = builder.buildCountHql();
                org.hibernate.query.Query<?> q = __.createQuery(countHql, Long.class);
                HqlQueryMethods.populateParameters(new SelectQueryDelegate(q), queryContext);
                return q.getSingleResult();
            }
            applyQuerySettings(delegate);
            List<?> results = delegate.list();
            return results.isEmpty() ? null : results.getFirst();
        });
    }

    protected void applyQuerySettings(HqlQueryDelegate d) {
        if (max != null && max > -1) {
            d.setMaxResults(max);
        }
        if (offset != null && offset > -1) {
            d.setFirstResult(offset);
        }
        populateQuerySettings(d, queryContext.querySettings());
        populateHints(d, queryContext.hints());
        HqlQueryMethods.populateParameters(d, queryContext);
    }

    public int executeUpdate() {
        throw new UnsupportedOperationException("SELECT query cannot be used for executeUpdate(); use a MutationHqlQuery instead");
    }

    protected IHibernateTemplate getHibernateTemplate() {
        HibernateSession hibernateSession = (HibernateSession) getSession();
        return (IHibernateTemplate) hibernateSession.getNativeInterface();
    }

    @Override
    protected List executeQuery(org.grails.datastore.mapping.model.PersistentEntity entity, Junction criteria) {
        return list();
    }

    public void setReadOnly(Boolean ignoredReadOnly) {
        // Compatibility method
    }

    public org.hibernate.query.Query<?> selectQuery() {
        return delegate.selectQuery();
    }

    @Override
    public Integer getMax() {
        if (max != null && max > -1) {
            return max;
        }
        Object m = queryContext.querySettings().get(HibernateQueryArgument.MAX.value());
        return m instanceof Number n ? n.intValue() : -1;
    }

    @Override
    public Integer getOffset() {
        if (offset != null && offset > -1) {
            return offset;
        }
        Object o = queryContext.querySettings().get(HibernateQueryArgument.OFFSET.value());
        return o instanceof Number n ? n.intValue() : 0;
    }
}
