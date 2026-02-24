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

import jakarta.persistence.FlushModeType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.groovy.parser.antlr4.util.StringUtils;
import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.exceptions.GrailsQueryException;

import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * A query implementation for HQL queries.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateHqlQuery extends Query {

  private org.hibernate.query.Query<?> query;

  public HibernateHqlQuery(
      Session session, PersistentEntity entity, org.hibernate.query.Query<?> query) {
    super(session, entity);
    this.query = query;
  }

  @Override
  protected void flushBeforeQuery() {
    // Hibernate handles flushing internally
  }

  @Override
  @SuppressWarnings("rawtypes")
  protected List executeQuery(PersistentEntity entity, Junction criteria) {
    Datastore datastore = getSession().getDatastore();
    ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
    publisher.publishEvent(new PreQueryEvent(datastore, this));
    if (uniqueResult) query.setMaxResults(1);
    List results = query.list();
    publisher.publishEvent(new PostQueryEvent(datastore, this, results));
    return results;
  }

  // ─── Static factory API ──────────────────────────────────────────────────

  /**
   * Session-bound step — creates the {@link org.hibernate.query.Query} from an open
   * {@link org.hibernate.Session} and wraps it in a {@link HibernateHqlQuery}.
   */
  @SuppressWarnings("unchecked")
  public static HibernateHqlQuery buildQuery(
      org.hibernate.Session session,
      HibernateDatastore dataStore,
      SessionFactory sessionFactory,
      PersistentEntity entity,
      HqlQueryContext ctx) {
    org.hibernate.query.Query<?> q;
    if (StringUtils.isEmpty(ctx.hql())) {
      q = session.createQuery("from " + ctx.targetClass().getName(), ctx.targetClass());
    } else if (ctx.isUpdate()) {
      q = session.createQuery(ctx.hql());
    } else {
      q = ctx.isNative()
          ? session.createNativeQuery(ctx.hql(), ctx.targetClass())
          : session.createQuery(ctx.hql(), ctx.targetClass());
    }
    HibernateHqlQuery result = new HibernateHqlQuery(
        new HibernateSession(dataStore, sessionFactory), entity, q);
    result.setFlushMode(session.getHibernateFlushMode());
    return result;
  }

  /**
   * Full factory — opens a session via the {@link GrailsHibernateTemplate}, builds the query
   * from the prepared {@link HqlQueryContext}, then applies settings and parameters.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static HibernateHqlQuery createHqlQuery(
      HibernateDatastore dataStore,
      SessionFactory sessionFactory,
      PersistentEntity entity,
      HqlQueryContext ctx,
      Map args,
      Collection positionalParams,
      GrailsHibernateTemplate template) {
    HibernateHqlQuery hqlQuery = template.execute(
        session -> buildQuery(session, dataStore, sessionFactory, entity, ctx));
    template.applySettings(hqlQuery.getQuery());
    hqlQuery.populateQuerySettings(MapUtils.isNotEmpty(args) ? new HashMap<>(args) : Collections.emptyMap());
    if (MapUtils.isNotEmpty(ctx.namedParams())) {
      hqlQuery.populateQueryWithNamedArguments(ctx.namedParams());
    } else if (CollectionUtils.isNotEmpty(positionalParams)) {
      hqlQuery.populateQueryWithIndexedArguments(List.copyOf(positionalParams));
    }
    return hqlQuery;
  }

  // ─── Query configuration ─────────────────────────────────────────────────

  public void setFlushMode(FlushMode flushMode) {
    session.setFlushMode(
        flushMode == FlushMode.AUTO || flushMode == FlushMode.ALWAYS
            ? FlushModeType.AUTO
            : FlushModeType.COMMIT);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void populateQuerySettings(Map<?, ?> args) {
    ifPresent(args, DynamicFinder.ARGUMENT_MAX,        v -> query.setMaxResults(toInt(v)));
    ifPresent(args, DynamicFinder.ARGUMENT_OFFSET,     v -> query.setFirstResult(toInt(v)));
    ifPresent(args, DynamicFinder.ARGUMENT_CACHE,      v -> query.setCacheable(toBool(v)));
    ifPresent(args, DynamicFinder.ARGUMENT_FETCH_SIZE, v -> query.setFetchSize(toInt(v)));
    ifPresent(args, DynamicFinder.ARGUMENT_TIMEOUT,    v -> query.setTimeout(toInt(v)));
    ifPresent(args, DynamicFinder.ARGUMENT_READ_ONLY,  v -> query.setReadOnly(toBool(v)));
    if (args.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
      Object v = args.get(DynamicFinder.ARGUMENT_FLUSH_MODE);
      if (v instanceof FlushMode fm) query.setHibernateFlushMode(fm);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void populateQueryWithNamedArguments(Map<?, ?> namedArgs) {
    if (namedArgs == null) return;
    namedArgs.forEach((key, value) -> {
      if (!(key instanceof CharSequence)) {
        throw new GrailsQueryException("Named parameter's name must be a String: " + namedArgs);
      }
      String name = key.toString();
      if (value == null) {
        query.setParameter(name, null);
      } else if (value instanceof Collection<?> col) {
        query.setParameterList(name, col);
      } else if (value.getClass().isArray()) {
        query.setParameterList(name, (Object[]) value);
      } else if (value instanceof CharSequence cs) {
        query.setParameter(name, cs.toString(), String.class);
      } else {
        query.setParameter(name, value);
      }
    });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void populateQueryWithIndexedArguments(List<?> params) {
    if (params == null) return;
    for (int i = 0; i < params.size(); i++) {
      Object val = params.get(i);
      if (val instanceof CharSequence cs)  query.setParameter(i + 1, cs.toString(), String.class);
      else if (val != null)                query.setParameter(i + 1, val);
      else                                 query.setParameter(i + 1, null);
    }
  }

  public org.hibernate.query.Query<?> getQuery() { return query; }

  public int executeUpdate() { return query.executeUpdate(); }

  // ─── Private utilities ────────────────────────────────────────────────────

  private static int     toInt(Object v)  { return Integer.parseInt(v.toString()); }
  private static boolean toBool(Object v) { return Boolean.parseBoolean(v.toString()); }

  private static void ifPresent(Map<?, ?> map, String key, java.util.function.Consumer<Object> action) {
    Object v = map.get(key);
    if (v != null) action.accept(v);
  }
}
