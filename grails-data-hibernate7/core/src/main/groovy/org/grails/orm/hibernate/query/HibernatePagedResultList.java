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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A PagedResultList for Hibernate 7.
 *
 * @since 8.0
 */
public class HibernatePagedResultList extends grails.gorm.PagedResultList {

    private final GrailsHibernatePersistentEntity entity;
    private final int max;
    private final int offset;

    public HibernatePagedResultList(HibernateQuery query) {
        super(query);
        this.entity = query.getEntity();
        this.max = resolveMax(query);
        this.offset = resolveOffset(query);
    }

    public HibernatePagedResultList(Query query) {
        super(query);
        this.entity = (GrailsHibernatePersistentEntity) query.getEntity();
        this.max = resolveMax(query);
        this.offset = resolveOffset(query);
    }

    public HibernatePagedResultList(GrailsHibernateTemplate template, GrailsHibernatePersistentEntity entity, Query query) {
        super(query);
        this.entity = entity;
        this.max = resolveMax(query);
        this.offset = resolveOffset(query);
    }

    public HibernatePagedResultList(GrailsHibernateTemplate template, PersistentEntity entity, Query query) {
        this(template, (GrailsHibernatePersistentEntity) entity, query);
    }

    private HibernatePagedResultList(GrailsHibernatePersistentEntity entity, int max, int offset, int totalCount, List resultList) {
        super(null);
        this.entity = entity;
        this.max = max;
        this.offset = offset;
        this.totalCount = totalCount;
        this.resultList = resultList;
    }

    public GrailsHibernatePersistentEntity getEntity() {
        return entity;
    }

    @Override
    public int getMax() {
        return max;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getTotalCount() {
        if (totalCount == Integer.MIN_VALUE) {
            Query query = getQuery();
            if (query == null) {
                totalCount = 0;
            } else if (query instanceof SelectHqlQuery hqlQuery) {
                // Static list uses HQL; cloning it as a criteria query loses its sort and fetch joins.
                String countHql = new HqlListQueryBuilder(entity, hqlQuery.queryContext.settings().settings()).buildCountHql();
                // The listeners of a query prepare the session for it, as the one that enables the filter of a
                // multi-tenant entity does. The total can be read in another session than the list was, or
                // for another tenant, so the count announces itself as the list did.
                Datastore datastore = hqlQuery.getSession().getDatastore();
                ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
                if (publisher != null) {
                    publisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
                }
                Number result = ((GrailsHibernateTemplate) hqlQuery.getHibernateTemplate()).execute(session ->
                        session.createSelectionQuery(countHql, Long.class).getSingleResult());
                totalCount = result.intValue();
            } else {
                Object clonedQuery = query.clone();
                if (!(clonedQuery instanceof Query)) {
                    totalCount = 0;
                } else {
                    Query newQuery = (Query) clonedQuery;
                    newQuery.offset(0);
                    newQuery.max(-1);
                    // The count builder suppresses ORDER BY while retaining joins required by the sort.
                    newQuery.projections().count();
                    Number result = (Number) newQuery.singleResult();
                    totalCount = result == null ? 0 : result.intValue();
                }
            }
        }
        return totalCount;
    }

    private Object writeReplace() throws ObjectStreamException {
        return new SerializationProxy(max, offset, getTotalCount(), resultList);
    }

    private static int resolveMax(Query query) {
        Integer queryMax = query == null ? null : query.getMax();
        return queryMax != null ? queryMax : -1;
    }

    private static int resolveOffset(Query query) {
        Integer queryOffset = query == null ? null : query.getOffset();
        return queryOffset != null ? queryOffset : 0;
    }

    private static final class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int max;
        private final int offset;
        private final int totalCount;
        private final List resultList;

        private SerializationProxy(int max, int offset, int totalCount, List resultList) {
            this.max = max;
            this.offset = offset;
            this.totalCount = totalCount;
            this.resultList = resultList;
        }

        private Object readResolve() {
            return new HibernatePagedResultList(null, max, offset, totalCount, resultList);
        }
    }
}
