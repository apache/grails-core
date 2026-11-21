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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;

public class PagedResultList extends grails.gorm.PagedResultList {

    private final CriteriaQuery criteriaQuery;
    private final Root queryRoot;
    private final CriteriaBuilder criteriaBuilder;
    private final PersistentEntity entity;
    private transient GrailsHibernateTemplate hibernateTemplate;

    public PagedResultList(GrailsHibernateTemplate template,
                           PersistentEntity entity,
                           HibernateHqlQuery hibernateHqlQuery,
                           CriteriaQuery criteriaQuery,
                           Root queryRoot,
                           CriteriaBuilder criteriaBuilder) {
        super(hibernateHqlQuery);
        hibernateTemplate = template;
        this.criteriaQuery = criteriaQuery;
        this.queryRoot = queryRoot;
        this.criteriaBuilder = criteriaBuilder;
        this.entity = entity;
    }

    @Override
    protected void initialize() {
        // no-op, already initialized
    }

    @Override
    public int getTotalCount() {
        if (totalCount == Integer.MIN_VALUE) {
            totalCount = hibernateTemplate.execute(new GrailsHibernateTemplate.HibernateCallback<>() {
                public Integer doInHibernate(Session session) throws HibernateException, SQLException {
                    // the list fetched associations, and a count has no entity to fetch them into: Hibernate rejects a
                    // fetch whose owner is not in the select list. Each fetch becomes a join of the same type, so an
                    // inner join keeps out of the total the rows it kept out of the page.
                    joinFetches(new ArrayList<>(queryRoot.getFetches()), queryRoot, new int[1]);
                    queryRoot.getFetches().clear();
                    final CriteriaQuery finalQuery = criteriaQuery.select(criteriaBuilder.count(queryRoot)).distinct(true).orderBy();
                    final Query query = session.createQuery(finalQuery);
                    hibernateTemplate.applySettings(query);
                    return ((Number) query.uniqueResult()).intValue();
                }
            });
        }
        return totalCount;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void joinFetches(List<Fetch> fetches, From target, int[] count) {
        for (Fetch fetch : fetches) {
            Join join = target.join(fetch.getAttribute().getName(), fetch.getJoinType());
            // the aliases of the list's query were fixed when it ran; a name of its own keeps the new joins from reusing one
            join.alias("pagedFetch" + count[0]++);
            joinFetches(new ArrayList<Fetch>(fetch.getFetches()), join, count);
        }
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
