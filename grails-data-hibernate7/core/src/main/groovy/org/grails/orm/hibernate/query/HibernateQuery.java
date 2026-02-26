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

import grails.gorm.DetachedCriteria;
import groovy.lang.Closure;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Projections;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.springframework.dao.InvalidDataAccessApiUsageException;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class HibernateQuery extends Query {
  protected static final String ALIAS = "_alias";
  protected String alias;
  protected int aliasCount;

  public DetachedCriteria<?> getDetachedCriteria() {
    return detachedCriteria;
  }

  protected Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<>();
  protected LinkedList<String> aliasStack = new LinkedList<>();
  protected LinkedList<PersistentEntity> entityStack = new LinkedList<PersistentEntity>();
  protected LinkedList<Association> associationStack = new LinkedList<Association>();
  protected DetachedCriteria<?> detachedCriteria;
  protected ProxyHandler proxyHandler = new HibernateProxyHandler();

  private Integer fetchSize;
  private Integer timeout;
  private FlushMode flushMode;
  private Boolean readOnly;

  public HibernateQuery(AbstractHibernateSession session, PersistentEntity entity) {
    super(session, entity);
    this.detachedCriteria = new DetachedCriteria(entity.getJavaClass());
  }

  public void setDetachedCriteria(DetachedCriteria<?> detachedCriteria) {
    this.detachedCriteria = detachedCriteria;
  }

  @Override
  protected Object resolveIdIfEntity(Object value) {
    // for Hibernate queries, the object itself is used in queries, not the id
    return value;
  }

  @Override
  public Query isEmpty(String property) {
    detachedCriteria.isEmpty(property);
    return this;
  }

  @Override
  public Query isNotEmpty(String property) {
    detachedCriteria.isNotEmpty(property);
    return this;
  }

  public Query count() {
    projections.count();
    return this;
  }

  @Override
  public Query isNull(String property) {
    detachedCriteria.isNull(property);
    return this;
  }

  @Override
  public Query isNotNull(String property) {
    detachedCriteria.isNotNull(property);
    return this;
  }

  @Override
  public PersistentEntity getEntity() {
    if (!entityStack.isEmpty()) {
      return entityStack.getLast();
    }
    return super.getEntity();
  }

  private String getAssociationPath(String propertyName) {
    if (propertyName.indexOf('.') > -1) {
      return propertyName;
    } else {
      StringBuilder fullPath = new StringBuilder();
      for (Association association : associationStack) {
        fullPath.append(association.getName());
        fullPath.append('.');
      }
      fullPath.append(propertyName);
      return fullPath.toString();
    }
  }

  public List<Criterion> getAllCriteria() {
    return detachedCriteria.getCriteria();
  }

  public void add(Criterion criterion) {
    detachedCriteria.add(criterion);
  }

  public void add(DetachedCriteria<?> detachedCriteria) {
    detachedCriteria.add(new Conjunction(detachedCriteria.getCriteria()));
  }

  public void add(Junction currentJunction, Criterion criterion) {
    Disjunction disjunction =
        (Disjunction)
            detachedCriteria.getCriteria().stream()
                .filter(it -> it instanceof Disjunction)
                .findFirst()
                .orElse(new Disjunction());
    disjunction.add(criterion);
    detachedCriteria.add(disjunction);
  }

  @Override
  public Query eq(String property, Object value) {
    detachedCriteria.eq(property, value);
    return this;
  }

  @Override
  public Query idEq(Object value) {
    detachedCriteria.idEq(value);
    return this;
  }

  @Override
  public Query gt(String property, Object value) {
    detachedCriteria.gt(property, value);
    return this;
  }

  @Override
  public Query and(Criterion a, Criterion b) {
    and(List.of(a, b));
    return this;
  }

  public Query and(List<Criterion> criteria) {
    var conjunction = new Conjunction();
    criteria.forEach(conjunction::add);
    detachedCriteria.add(conjunction);
    return this;
  }

  public Query and(Closure closure) {
    detachedCriteria.and(closure);
    return this;
  }

  @Override
  public Query or(Criterion a, Criterion b) {
    or(List.of(a, b));
    return this;
  }

  public Query or(List<Criterion> criteria) {
    var disjunction = new Disjunction();
    criteria.forEach(disjunction::add);
    detachedCriteria.add(disjunction);
    return this;
  }

  public Query or(Closure closure) {
    detachedCriteria.or(closure);
    return this;
  }

  public Query not(Criterion a) {
    not(
        new Closure(HibernateQuery.this) {
          public void doCall() {
            ((DetachedCriteria) getDelegate()).add(a);
          }
        });
    return this;
  }

  public Query not(List<Criterion> criteria) {
    var conjunction = new Conjunction();
    criteria.forEach(conjunction::add);
    var negation = new Negation();
    negation.add(conjunction);
    detachedCriteria.add(negation);
    return this;
  }

  public Query not(Closure closure) {
    detachedCriteria.not(closure);
    return this;
  }

  @Override
  public Query allEq(Map<String, Object> values) {
    values.forEach(
        (key, value) -> {
          detachedCriteria.eq(key, value);
        });
    return this;
  }

  @Override
  public Query ge(String property, Object value) {
    detachedCriteria.ge(property, value);
    return this;
  }

  @Override
  public Query le(String property, Object value) {
    detachedCriteria.le(property, value);
    return this;
  }

  @Override
  public Query gte(String property, Object value) {
    detachedCriteria.gte(property, value);
    return this;
  }

  @Override
  public Query lte(String property, Object value) {
    detachedCriteria.lte(property, value);
    return this;
  }

  @Override
  public Query lt(String property, Object value) {
    detachedCriteria.lt(property, value);
    return this;
  }

  @Override
  public Query in(String property, List values) {
    detachedCriteria.in(property, values);
    return this;
  }

  @Override
  public Query between(String property, Object start, Object end) {
    detachedCriteria.between(property, start, end);
    return this;
  }

  @Override
  public Query like(String property, String expr) {
    detachedCriteria.like(property, expr);
    return this;
  }

  @Override
  public Query ilike(String property, String expr) {
    detachedCriteria.ilike(property, expr);
    return this;
  }

  @Override
  public Query rlike(String property, String expr) {
    detachedCriteria.rlike(property, expr);
    return this;
  }

  @Override
  public AssociationQuery createQuery(String associationName) {
    final PersistentProperty property =
        entity.getPropertyByName(calculatePropertyName(associationName));
    if ((property instanceof Association association)) {
      String alias = generateAlias(associationName);
      CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, alias);
      return new HibernateAssociationQuery(
          (AbstractHibernateSession) getSession(),
          association.getAssociatedEntity(),
          association,
          subCriteria.associationPath,
          alias);
    }
    throw new InvalidDataAccessApiUsageException(
        "Cannot query association ["
            + calculatePropertyName(associationName)
            + "] of entity ["
            + entity
            + "]. Property is not an association!");
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  private CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
    String associationPath = getAssociationPath(associationName);
    String effectiveAlias = (alias == null) ? generateAlias(associationName) : alias;

    if (createdAssociationPaths.containsKey(associationPath)) {
      return createdAssociationPaths.get(associationPath);
    } else {
      CriteriaQuery criteriaQuery = getCriteriaBuilder().createQuery(entity.getJavaClass());
      CriteriaAndAlias subCriteria = new CriteriaAndAlias(criteriaQuery, effectiveAlias, associationPath);
      createdAssociationPaths.put(associationPath, subCriteria);
      createdAssociationPaths.put(effectiveAlias, subCriteria);
      return subCriteria;
    }
  }

  @Override
  public Query firstResult(int offset) {
    offset(offset);
    return this;
  }

  @Override
  public Query cache(boolean cache) {
    return super.cache(cache);
  }

  @Override
  public Query lock(boolean lock) {
    return super.lock(lock);
  }

  @Override
  public Query order(Order order) {
    // TODO HACK
    if (order == null) {
      detachedCriteria.getOrders().clear();
    } else {
      detachedCriteria.order(order);
    }
    return this;
  }

  @Override
  public Query join(String property) {
    detachedCriteria.join(property);
    return this;
  }

  @Override
  public Query join(String property, JoinType joinType) {
    detachedCriteria.join(property, joinType);
    return this;
  }

  @Override
  public Query select(String property) {
    detachedCriteria.select(property);
    // Ensure property is added to projections for Hibernate 7
    projections.property(property);
    return this;
  }

  @Override
  public List list() {
    return getHibernateQueryExecutor().list(getCurrentSession(), getJpaCriteriaQuery());
  }

  public List list(Session session) {
    return getHibernateQueryExecutor().list(session, getJpaCriteriaQuery());
  }

  private HibernateQueryExecutor getHibernateQueryExecutor() {
    return new HibernateQueryExecutor(
        offset, max, lockResult, queryCache, fetchSize, timeout, flushMode, readOnly, proxyHandler);
  }

  public JpaCriteriaQuery<?> getJpaCriteriaQuery() {
    return new JpaCriteriaQueryCreator(projections, getCriteriaBuilder(), entity, detachedCriteria)
        .createQuery();
  }

  public void setFetchSize(Integer fetchSize) {
    this.fetchSize = fetchSize;
  }

  @Override
  protected void flushBeforeQuery() {
    // do nothing
  }

  @Override
  public Object singleResult() {
    return getHibernateQueryExecutor().singleResult(getCurrentSession(), getJpaCriteriaQuery());
  }

  public Object singleResult(Session session) {
    return getHibernateQueryExecutor().singleResult(session, getJpaCriteriaQuery());
  }

  public Object scroll() {
    return getHibernateQueryExecutor().scroll(getCurrentSession(), getJpaCriteriaQuery());
  }

  public Object scroll(Session session) {
    return getHibernateQueryExecutor().scroll(session, getJpaCriteriaQuery());
  }

  private Session getCurrentSession() {
    return getSessionFactory().getCurrentSession();
  }

  private SessionFactory getSessionFactory() {
    return ((IHibernateTemplate) session.getNativeInterface()).getSessionFactory();
  }

  public HibernateCriteriaBuilder getCriteriaBuilder() {
    return getSessionFactory().getCriteriaBuilder();
  }

  @Override
  protected List executeQuery(PersistentEntity entity, Junction criteria) {
    return list();
  }

  protected String calculatePropertyName(String property) {
    if (alias == null) {
      return property;
    }
    return alias + '.' + property;
  }

  protected String generateAlias(String associationName) {
    return calculatePropertyName(associationName) + calculatePropertyName(ALIAS) + aliasCount++;
  }

  public Query in(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.inList(propertyName, subquery);
    return this;
  }

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }

  public void setHibernateFlushMode(FlushMode flushMode) {
    this.flushMode = flushMode;
  }

  public void setReadOnly(Boolean readOnly) {
    this.readOnly = readOnly;
  }

  public DetachedCriteria<?> getHibernateCriteria() {
    return detachedCriteria;
  }

  public Query notIn(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.notIn(propertyName, subquery);
    return this;
  }

  public Query exists(QueryableCriteria<?> subquery) {
    detachedCriteria.exists(subquery);
    return this;
  }

  public Query notExits(QueryableCriteria<?> subquery) {
    detachedCriteria.notExists(subquery);
    return this;
  }

  public Query gtAll(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.gtAll(propertyName, subquery);
    return this;
  }

  public Query geAll(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.geAll(propertyName, subquery);
    return this;
  }

  public Query ltAll(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.ltAll(propertyName, subquery);
    return this;
  }

  public Query leAll(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.leAll(propertyName, subquery);
    return this;
  }

  public Query gtSome(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.gtSome(propertyName, subquery);
    return this;
  }

  public Query geSome(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.geSome(propertyName, subquery);
    return this;
  }

  public Query ltSome(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.ltSome(propertyName, subquery);
    return this;
  }

  public Query leSome(String propertyName, QueryableCriteria<?> subquery) {
    detachedCriteria.leSome(propertyName, subquery);
    return this;
  }

  public Query eqAll(String propertyName, QueryableCriteria propertyValue) {
    detachedCriteria.eqAll(propertyName, propertyValue);
    return this;
  }

  public Query ne(String propertyName, Object propertyValue) {
    detachedCriteria.ne(propertyName, propertyValue);
    return this;
  }

  public Query eqProperty(String propertyName, String otherPropertyName) {
    detachedCriteria.eqProperty(propertyName, otherPropertyName);
    return this;
  }

  public Query neProperty(String propertyName, String otherPropertyName) {
    detachedCriteria.neProperty(propertyName, otherPropertyName);
    return this;
  }

  public Query gtProperty(String propertyName, String otherPropertyName) {
    detachedCriteria.gtProperty(propertyName, otherPropertyName);
    return this;
  }

  public Query geProperty(String propertyName, String otherPropertyName) {
    detachedCriteria.geProperty(propertyName, otherPropertyName);
    return this;
  }

  public Query ltProperty(String propertyName, String otherPropertyName) {
    detachedCriteria.ltProperty(propertyName, otherPropertyName);
    return this;
  }

  public Query leProperty(String propertyName, String otherPropertyName) {
    detachedCriteria.leProperty(propertyName, otherPropertyName);
    return this;
  }

  public Query sizeEq(String propertyName, int size) {
    detachedCriteria.sizeEq(propertyName, size);
    return this;
  }

  public Query sizeGt(String propertyName, int size) {
    detachedCriteria.sizeGt(propertyName, size);
    return this;
  }

  public Query sizeGe(String propertyName, int size) {
    detachedCriteria.sizeGe(propertyName, size);
    return this;
  }

  public Query sizeLe(String propertyName, int size) {
    detachedCriteria.sizeLe(propertyName, size);
    return this;
  }

  public Query sizeLt(String propertyName, int size) {
    detachedCriteria.sizeLt(propertyName, size);
    return this;
  }

  public Query sizeNe(String propertyName, int size) {
    detachedCriteria.sizeNe(propertyName, size);
    return this;
  }

  public Query maxResults(int maxResults) {
    this.max = maxResults;
    return this;
  }

  public Query distinct() {
    projections.add(Projections.distinct());
    return this;
  }

  @Override
  @SuppressWarnings("PMD.CloneThrowsCloneNotSupportedException")
  public HibernateQuery clone() {
    final HibernateSession hibernateSession = (HibernateSession) getSession();
    final GrailsHibernateTemplate hibernateTemplate =
        (GrailsHibernateTemplate) hibernateSession.getNativeInterface();
    return (HibernateQuery)
        hibernateTemplate.execute(
            (GrailsHibernateTemplate.HibernateCallback<Object>)
                session -> {
                  HibernateQuery hibernateQuery = new HibernateQuery(hibernateSession, entity);
                  if (this.max != null && this.max > 0) {
                    hibernateQuery.max(this.max);
                  }
                  if (this.offset != null && this.offset > 0) {
                    hibernateQuery.offset(this.offset);
                  }
                  hibernateQuery.setDetachedCriteria(this.detachedCriteria.clone());

                  return hibernateQuery;
                });
  }
}
