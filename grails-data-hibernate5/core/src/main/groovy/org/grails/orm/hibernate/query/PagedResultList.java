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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
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
                    // a count does not select the associations that the list fetched, and Hibernate rejects
                    // a fetch of an association whose owner the query does not select. A sort through a fetched
                    // association joins from the fetch, and the list pages through the rows of that join
                    if (!queryRoot.getFetches().isEmpty()) {
                        joinWhatIsJoinedFromFetches(queryRoot, queryRoot, "count");
                        queryRoot.getFetches().clear();
                    }
                    // a list that fetches a collection returns each instance once, whatever a sort joins; any other
                    // list returns a row for each element of a collection that it sorts by, and pages through those rows
                    final Expression count = criteriaQuery.isDistinct() ? criteriaBuilder.countDistinct(queryRoot) : criteriaBuilder.count(queryRoot);
                    final CriteriaQuery finalQuery = criteriaQuery.select(count).orderBy();
                    final Query query = session.createQuery(finalQuery);
                    hibernateTemplate.applySettings(query);
                    return ((Number) query.uniqueResult()).intValue();
                }
            });
        }
        return totalCount;
    }

    /**
     * Joins, from the given target, the fetches of the given parent that have joins of their own, and
     * those joins. Each join gets an alias from its path, since Hibernate would generate one that the
     * root of the query already has from rendering the list.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void joinWhatIsJoinedFromFetches(From target, From parent, String aliasPrefix) {
        for (Object existing : parent.getFetches()) {
            if (existing instanceof From && hasJoins((From) existing)) {
                From fetch = (From) existing;
                String name = ((Fetch) fetch).getAttribute().getName();
                String alias = aliasPrefix + '_' + name;
                Join copy = target.join(name, ((Fetch) fetch).getJoinType());
                copy.alias(alias);
                copyJoins(copy, fetch, alias);
                joinWhatIsJoinedFromFetches(copy, fetch, alias);
            }
        }
    }

    private static boolean hasJoins(From fetch) {
        if (!fetch.getJoins().isEmpty()) {
            return true;
        }
        for (Object nested : fetch.getFetches()) {
            if (nested instanceof From && hasJoins((From) nested)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void copyJoins(From target, From source, String aliasPrefix) {
        for (Object existing : source.getJoins()) {
            Join join = (Join) existing;
            String alias = aliasPrefix + '_' + join.getAttribute().getName();
            Join copy = target.join(join.getAttribute().getName(), join.getJoinType());
            copy.alias(alias);
            copyJoins(copy, join, alias);
        }
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
