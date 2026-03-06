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
import jakarta.persistence.criteria.Root;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;

public class PagedResultList extends grails.gorm.PagedResultList {

    private static final long serialVersionUID = 1L;

  private final PersistentEntity entity;
  private transient GrailsHibernateTemplate hibernateTemplate;

  // CriteriaQuery-based count (legacy path)
  private final CriteriaQuery criteriaQuery;
  private final Root queryRoot;
  private final CriteriaBuilder criteriaBuilder;

  // HQL-based count (new path)
  private final String countHql;

  /** Legacy constructor — count via CriteriaQuery. */
  public PagedResultList(
      GrailsHibernateTemplate template,
      PersistentEntity entity,
      HibernateHqlQuery hibernateHqlQuery,
      CriteriaQuery criteriaQuery,
      Root queryRoot,
      CriteriaBuilder criteriaBuilder) {
    super(hibernateHqlQuery);
    this.hibernateTemplate = template;
    this.entity = entity;
    this.criteriaQuery = criteriaQuery;
    this.queryRoot = queryRoot;
    this.criteriaBuilder = criteriaBuilder;
    this.countHql = null;
  }

  /** HQL constructor — count via scalar HQL. */
  public PagedResultList(
      GrailsHibernateTemplate template,
      PersistentEntity entity,
      HibernateHqlQuery hibernateHqlQuery) {
    super(hibernateHqlQuery);
    this.hibernateTemplate = template;
    this.entity = entity;
    this.countHql = HibernateHqlQuery.buildCountHql(entity);
    this.criteriaQuery = null;
    this.queryRoot = null;
    this.criteriaBuilder = null;
  }

    // HQL-based count (new path)
    private final String countHql;

  @Override
  public int getTotalCount() {
    if (totalCount == Integer.MIN_VALUE) {
      totalCount =
          countHql != null ? countViaHql() : countViaCriteria();
    }

  private int countViaHql() {
    return hibernateTemplate.execute(session -> {
      Query<?> q = session.createQuery(countHql);
      hibernateTemplate.applySettings(q);
      return ((Number) q.uniqueResult()).intValue();
    });
  }

  private int countViaCriteria() {
    return hibernateTemplate.execute(
        new GrailsHibernateTemplate.HibernateCallback<Integer>() {
          public Integer doInHibernate(Session session)
              throws HibernateException, SQLException {
            final CriteriaQuery finalQuery =
                criteriaQuery
                    .select(criteriaBuilder.count(queryRoot))
                    .distinct(true)
                    .orderBy();
            final Query query = session.createQuery(finalQuery);
            hibernateTemplate.applySettings(query);
            return ((Number) query.uniqueResult()).intValue();
          }
        });
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }
}
