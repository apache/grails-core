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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;

import grails.gorm.PagedList;

import org.hibernate.query.Query;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;

/**
 * A {@link grails.gorm.PagedResultList} implementation for Hibernate.
 *
 * @param <E> The element type
 */
public class HibernatePagedResultList<E> implements PagedList<E> {

    @Serial
    private static final long serialVersionUID = 1L;
    // HQL-based count (new path)
    private final String countHql;
    private final transient GrailsHibernateTemplate hibernateTemplate;
    private final transient PersistentEntity entity;
    private final Integer max;
    private int offset;
    protected List<E> resultList;
    protected int totalCount = Integer.MIN_VALUE;

    @SuppressWarnings({"unchecked", "PMD.NullAssignment"})
    public HibernatePagedResultList(org.grails.datastore.mapping.query.Query query) {
        this.entity = query.getEntity();
        this.hibernateTemplate = query instanceof HibernateQuery hibernateQuery ?
                hibernateQuery.getHibernateTemplate() :
                (query instanceof HibernateHqlQuery hibernateHqlQuery ?
                        hibernateHqlQuery.getHibernateTemplate() :
                        null);
        this.max = query.getMax();
        Integer offsetParam = query.getOffset();
        this.offset = offsetParam != null ? offsetParam : 0;
        this.resultList = query.list();
        this.countHql = null;
    }

    /** HQL constructor — count via scalar HQL. */
    @SuppressWarnings({"unchecked", "PMD.NullAssignment"})
    public HibernatePagedResultList(
            GrailsHibernateTemplate template, PersistentEntity entity, HibernateHqlQuery hibernateHqlQuery) {
        this.hibernateTemplate = template;
        this.entity = entity;
        this.max = hibernateHqlQuery.getMax();
        Integer offsetParam = hibernateHqlQuery.getOffset();
        this.offset = offsetParam != null ? offsetParam : 0;
        this.resultList = hibernateHqlQuery.list();
        this.countHql = HibernateHqlQuery.buildCountHql(entity);
    }

    @Override
    public List<E> getResultList() {
        return resultList;
    }

    protected void initialize() {
        // no-op, already initialized
    }

    @Override
    public int getTotalCount() {
        if (totalCount == Integer.MIN_VALUE) {
            totalCount = countViaHql();
        }
        return totalCount;
    }

    public Integer getMax() {
        return max;
    }

    public int getOffset() {
        return offset;
    }

    private int countViaHql() {
        if (hibernateTemplate == null || entity == null) {
            return 0;
        }
        return hibernateTemplate.execute(session -> {
            String hql = countHql != null ? countHql : HibernateHqlQuery.buildCountHql(entity);
            Query<?> q = session.createQuery(hql, Long.class);
            hibernateTemplate.applySettings(q);
            return ((Number) q.uniqueResult()).intValue();
        });
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        getTotalCount();
        out.defaultWriteObject();
    }
}
