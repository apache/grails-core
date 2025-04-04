/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.sql.SQLException;

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
            totalCount = hibernateTemplate.execute(new GrailsHibernateTemplate.HibernateCallback<Integer>() {
                public Integer doInHibernate(Session session) throws HibernateException, SQLException {
                    final CriteriaQuery finalQuery = criteriaQuery.select(criteriaBuilder.count(queryRoot)).distinct(true).orderBy();
                    final Query query = session.createQuery(finalQuery);
                    hibernateTemplate.applySettings(query);
                    return ((Number)query.uniqueResult()).intValue();
                }
            });
        }
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
