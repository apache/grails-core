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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.QueryFlushMode;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A query implementation for HQL mutation queries (UPDATE/DELETE).
 *
 * @author Graeme Rocher
 * @since 7.0.0
 */
@SuppressWarnings("rawtypes")
//TODO CLEANUP
public class MutationHqlQuery extends Query {

    protected static final Set<String> INTERNAL_SETTINGS = Set.of(
            HibernateQueryArgument.FLUSH_MODE.value(),
            HibernateQueryArgument.CACHE.value(),
            HibernateQueryArgument.TIMEOUT.value(),
            HibernateQueryArgument.READ_ONLY.value(),
            HibernateQueryArgument.FETCH_SIZE.value(),
            HibernateQueryArgument.MAX.value(),
            HibernateQueryArgument.OFFSET.value()
    );

    private final HqlQueryContext queryContext;
    private final String hql;
    private final HqlQueryDelegate delegate;

    public MutationHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext) {
        super(session, entity);
        this.queryContext = queryContext;
        this.hql = null;
        this.delegate = null;
    }

    public MutationHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext, HqlQueryDelegate delegate) {
        super(session, entity);
        this.queryContext = queryContext;
        this.delegate = delegate;
        this.hql = null;
    }

    public MutationHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, String hql) {
        super(session, entity);
        this.queryContext = null;
        this.hql = hql;
        this.delegate = null;
    }

    public MutationHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, MutationQuery mutationQuery) {
        super(session, entity);
        this.queryContext = null;
        this.hql = null;
        this.delegate = new MutationQueryDelegate(mutationQuery);
    }

    public int executeUpdate() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return (Integer) template.execute(new GrailsHibernateTemplate.HibernateCallback<Integer>() {
            @Override
            public Integer doInHibernate(Session session) {
                HqlQueryDelegate d = getOrCreateDelegate(session);
                return d.executeUpdate();
            }
        });
    }

    protected HqlQueryDelegate getOrCreateDelegate(Session session) {
        HqlQueryDelegate d = delegate;
        if (d == null) {
            d = new MutationQueryDelegate(session.createMutationQuery(queryContext != null ? queryContext.hql() : hql));
        }
        applyQuerySettings(d);
        return d;
    }

    protected void applyQuerySettings(HqlQueryDelegate d) {
        if (queryContext != null) {
            populateQuerySettings(d, queryContext.querySettings());
            populateParameters(d);
        }
    }

    protected void populateQuerySettings(HqlQueryDelegate d, Map<String, Object> args) {
        if (args == null || args.isEmpty()) return;
        if (args.containsKey(HibernateQueryArgument.FLUSH_MODE.value())) {
            d.setQueryFlushMode(HibernateHqlQuery.convertQueryFlushMode(args.get(HibernateQueryArgument.FLUSH_MODE.value())));
        }
    }

    protected void populateParameters(HqlQueryDelegate d) {
        if (queryContext.namedParams() != null && !queryContext.namedParams().isEmpty()) {
            queryContext.namedParams().forEach((key, value) -> {
                if (INTERNAL_SETTINGS.contains(key)) return;
                Object val = HibernateHqlQuery.convertValue(value);
                if (val instanceof Collection) {
                    d.setParameterList(key, (Collection) val);
                } else if (val != null && val.getClass().isArray()) {
                    d.setParameterList(key, (Object[]) val);
                } else {
                    d.setParameter(key, val);
                }
            });
        } else if (queryContext.positionalParams() != null && !queryContext.positionalParams().isEmpty()) {
            for (int i = 0; i < queryContext.positionalParams().size(); i++) {
                Object val = HibernateHqlQuery.convertValue(queryContext.positionalParams().get(i));
                d.setParameter(i + 1, val);
            }
        }
    }

    public void populateQuerySettings(Map<String, Object> args, ConversionService conversionService) {
        if (args == null || args.isEmpty()) return;
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        template.execute(session -> {
            HqlQueryDelegate d = getOrCreateDelegate(session);
            populateQuerySettings(d, args);
            return null;
        });
    }

    public void populateQueryWithNamedArguments(Map<String, Object> namedArgs) {
        if (namedArgs == null || namedArgs.isEmpty()) return;
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        template.execute(session -> {
            HqlQueryDelegate d = getOrCreateDelegate(session);
            namedArgs.forEach((key, value) -> {
                if (INTERNAL_SETTINGS.contains(key)) return;
                Object val = HibernateHqlQuery.convertValue(value);
                if (val instanceof Collection) {
                    d.setParameterList(key, (Collection) val);
                } else if (val != null && val.getClass().isArray()) {
                    d.setParameterList(key, (Object[]) val);
                } else {
                    d.setParameter(key, val);
                }
            });
            return null;
        });
    }

    public void populateQueryWithIndexedArguments(List<Object> positionalParams) {
        if (positionalParams == null || positionalParams.isEmpty()) return;
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        template.execute(session -> {
            HqlQueryDelegate d = getOrCreateDelegate(session);
            for (int i = 0; i < positionalParams.size(); i++) {
                Object val = HibernateHqlQuery.convertValue(positionalParams.get(i));
                d.setParameter(i + 1, val);
            }
            return null;
        });
    }

    @Override
    public List list() {
        throw new UnsupportedOperationException("Mutation query (UPDATE/DELETE) cannot be used for list(); use executeUpdate() instead");
    }

    @Override
    public Object singleResult() {
        throw new UnsupportedOperationException("Mutation query (UPDATE/DELETE) cannot be used for singleResult(); use executeUpdate() instead");
    }

    private IHibernateTemplate getHibernateTemplate() {
        HibernateSession hibernateSession = (HibernateSession) getSession();
        return (IHibernateTemplate) hibernateSession.getNativeInterface();
    }

    @Override
    protected List executeQuery(org.grails.datastore.mapping.model.PersistentEntity entity, Junction criteria) {
        throw new UnsupportedOperationException("Mutation query (UPDATE/DELETE) cannot be used for executeQuery(); use executeUpdate() instead");
    }

    public org.hibernate.query.Query selectQuery() {
        return null;
    }
}
