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

import jakarta.persistence.criteria.CriteriaQuery;
import java.util.List;
import java.util.Map;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.AbstractHibernateSession;

class HibernateAssociationQuery extends AssociationQuery {

  protected String alias;
  protected CriteriaQuery assocationCriteria;

  public HibernateAssociationQuery(
      CriteriaQuery criteria,
      AbstractHibernateSession session,
      PersistentEntity associatedEntity,
      Association association,
      String alias) {
    super(session, associatedEntity, association);
    this.alias = alias;
    assocationCriteria = criteria;
  }

  @Override
  public Query order(Order order) {
    return this;
  }

  @Override
  public Query isEmpty(String property) {
    return this;
  }

  @Override
  public Query isNotEmpty(String property) {
    return this;
  }

  @Override
  public Query isNull(String property) {
    return this;
  }

  @Override
  public Query isNotNull(String property) {
    return this;
  }

  @Override
  public void add(Criterion criterion) {}

  @Override
  public Junction disjunction() {
    return null;
  }

  @Override
  public Junction negation() {
    return null;
  }

  @Override
  public Query eq(String property, Object value) {
    return this;
  }

  @Override
  public Query idEq(Object value) {
    return this;
  }

  @Override
  public Query gt(String property, Object value) {
    return this;
  }

  @Override
  public Query and(Criterion a, Criterion b) {
    return this;
  }

  @Override
  public Query or(Criterion a, Criterion b) {
    return this;
  }

  @Override
  public Query allEq(Map<String, Object> values) {
    return this;
  }

  @Override
  public Query ge(String property, Object value) {
    return this;
  }

  @Override
  public Query le(String property, Object value) {
    return this;
  }

  @Override
  public Query gte(String property, Object value) {
    return this;
  }

  @Override
  public Query lte(String property, Object value) {
    return this;
  }

  @Override
  public Query lt(String property, Object value) {
    return this;
  }

  @Override
  public Query in(String property, List values) {
    return this;
  }

  @Override
  public Query between(String property, Object start, Object end) {
    return this;
  }

  @Override
  public Query like(String property, String expr) {
    return this;
  }

  @Override
  public Query ilike(String property, String expr) {
    return this;
  }

  @Override
  public Query rlike(String property, String expr) {
    return this;
  }
}
