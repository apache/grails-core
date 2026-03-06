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
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.groovy.parser.antlr4.util.StringUtils;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.groovy.parser.antlr4.util.StringUtils;
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
import org.hibernate.query.QueryFlushMode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;

/**
 * A query implementation for HQL queries.
 *
 * <p>Hibernate 7 splits query types into {@link org.hibernate.query.Query} (SELECT) and
 * {@link org.hibernate.query.MutationQuery} (UPDATE/DELETE), which are siblings under
 * {@link org.hibernate.query.CommonQueryContract}. This class uses composition via
 * {@link HqlQueryDelegate} to eliminate runtime type-checks and null-field branching.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class HibernateHqlQuery extends Query {

  /** Handles all query operations; the concrete type encodes whether this is SELECT or UPDATE/DELETE. */
  private final HqlQueryDelegate delegate;

  /** Constructs a SELECT query wrapper. */
  public HibernateHqlQuery(
      Session session, PersistentEntity entity, org.hibernate.query.Query<?> query) {
    super(session, entity);
    this.delegate = new SelectQueryDelegate(query);
  }

  /** Constructs an UPDATE/DELETE query wrapper. */
  public HibernateHqlQuery(
      Session session, PersistentEntity entity, org.hibernate.query.MutationQuery mutationQuery) {
    super(session, entity);
    this.delegate = new MutationQueryDelegate(mutationQuery);
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
    if (uniqueResult) delegate.setMaxResults(1);
    List results = delegate.list();
    publisher.publishEvent(new PostQueryEvent(datastore, this, results));
    return results;
  }

  // ─── Static factory API ──────────────────────────────────────────────────

  /**
   * Session-bound step — creates the appropriate Hibernate query from an open {@link
   * org.hibernate.Session} and wraps it in a {@link HibernateHqlQuery}.
   */
  protected static HibernateHqlQuery buildQuery(
      org.hibernate.Session session,
      HibernateDatastore dataStore,
      SessionFactory sessionFactory,
      PersistentEntity entity,
      HqlQueryContext ctx) {
    HibernateSession hibernateSession = new HibernateSession(dataStore, sessionFactory);
    if (StringUtils.isEmpty(ctx.hql())) {
      var q = session.createQuery("from " + ctx.targetClass().getName(), ctx.targetClass());
      return new HibernateHqlQuery(hibernateSession, entity, q);
    } else if (ctx.isUpdate()) {
      var mq = session.createMutationQuery(ctx.hql());
      var result = new HibernateHqlQuery(hibernateSession, entity, mq);
      result.setFlushMode(session.getHibernateFlushMode());
      return result;
    } else {
      var q =
          ctx.isNative()
              ? session.createNativeQuery(ctx.hql(), ctx.targetClass())
              : isScalarSelect(ctx.hql())
                  ? session.createQuery(ctx.hql())
                  : session.createQuery(ctx.hql(), ctx.targetClass());
      var result = new HibernateHqlQuery(hibernateSession, entity, q);
      result.setFlushMode(session.getHibernateFlushMode());
      return result;
    }
  }

  /** Returns true if the HQL starts with a {@code select} clause that doesn't project the entity. */
  private static boolean isScalarSelect(String hql) {
    String trimmed = hql.stripLeading().toLowerCase();
    return trimmed.startsWith("select") && !trimmed.startsWith("select e ") && !trimmed.startsWith("select e,");
  }

  /**
   * Full factory — opens a session via the {@link GrailsHibernateTemplate}, builds the query from
   * the prepared {@link HqlQueryContext}, then applies settings and parameters.
   */
  protected static HibernateHqlQuery create(
      HibernateDatastore dataStore,
      SessionFactory sessionFactory,
      PersistentEntity entity,
      HqlQueryContext ctx,
      GrailsHibernateTemplate template,
      ConversionService conversionService) {
    HibernateHqlQuery hqlQuery =
        template.execute(session -> buildQuery(session, dataStore, sessionFactory, entity, ctx));
    var selectQuery = hqlQuery.selectQuery();
    if (selectQuery != null) {
      template.applySettings(selectQuery);
    }
    hqlQuery.populateQuerySettings(
        MapUtils.isNotEmpty(ctx.querySettings()) ? new HashMap<>(ctx.querySettings()) : Collections.emptyMap(),
        conversionService);
    if (MapUtils.isNotEmpty(ctx.namedParams())) {
      hqlQuery.populateQueryWithNamedArguments(ctx.namedParams());
    } else if (CollectionUtils.isNotEmpty(ctx.positionalParams())) {
      hqlQuery.populateQueryWithIndexedArguments(List.copyOf(ctx.positionalParams()));
    }

  public static HibernateHqlQuery createHqlQuery(
      HibernateDatastore dataStore,
      SessionFactory sessionFactory,
      PersistentEntity entity,
      HqlQueryContext ctx,
      GrailsHibernateTemplate template,
      ConversionService conversionService) {
    return create(dataStore, sessionFactory, entity, ctx, template, conversionService);
  }


  /**
   * Builds the count HQL string used by {@link PagedResultList} when paging is requested.
   */
  public static String buildCountHql(PersistentEntity entity) {
    return new HqlListQueryBuilder(entity, Collections.emptyMap()).buildCountHql();
  }

  // ─── Query configuration ─────────────────────────────────────────────────

  protected void setFlushMode(FlushMode flushMode) {
    session.setFlushMode(
        flushMode == FlushMode.AUTO || flushMode == FlushMode.ALWAYS
            ? FlushModeType.AUTO
            : FlushModeType.COMMIT);
  }

  protected void populateQuerySettings(Map<?, ?> args, ConversionService conversionService) {
    ifPresent(args, HibernateQueryArgument.MAX.value(), v -> delegate.setMaxResults(toInt(v, conversionService)));
    ifPresent(args, HibernateQueryArgument.OFFSET.value(), v -> delegate.setFirstResult(toInt(v, conversionService)));
    ifPresent(args, HibernateQueryArgument.CACHE.value(), v -> delegate.setCacheable(toBool(v)));
    ifPresent(args, HibernateQueryArgument.FETCH_SIZE.value(), v -> delegate.setFetchSize(toInt(v, conversionService)));
    ifPresent(args, HibernateQueryArgument.TIMEOUT.value(), v -> delegate.setTimeout(toInt(v, conversionService)));
    ifPresent(args, HibernateQueryArgument.READ_ONLY.value(), v -> delegate.setReadOnly(toBool(v)));
    ifPresent(args, HibernateQueryArgument.FLUSH_MODE.value(), v -> delegate.setQueryFlushMode(convertQueryFlushMode(v)));
    if (toBoolFromMap(args, HibernateQueryArgument.LOCK.value())) {
      delegate.setLockMode(LockModeType.PESSIMISTIC_WRITE);
      delegate.setCacheable(false);
    } else {
      if (!args.containsKey(HibernateQueryArgument.CACHE.value())) {
        org.grails.orm.hibernate.cfg.Mapping m =
            org.grails.orm.hibernate.cfg.MappingCacheHolder.getInstance()
                .getMapping(getEntity().getJavaClass());
        if (m != null && m.getCache() != null && m.getCache().getEnabled()) {
          delegate.setCacheable(true);
        }
      }
    }
  }

  public static QueryFlushMode convertQueryFlushMode(Object object) {
    FlushMode fm = convertFlushMode(object);
    if (fm == null) return QueryFlushMode.DEFAULT;
    return switch (fm) {
      case ALWAYS -> QueryFlushMode.FLUSH;
      case MANUAL, COMMIT -> QueryFlushMode.NO_FLUSH;
      default -> QueryFlushMode.DEFAULT;
    };
  }

  public static FlushMode convertFlushMode(Object object) {
    if (object == null) return null;
    if (object instanceof FlushMode flushMode) return flushMode;
    try {
      return FlushMode.valueOf(object.toString());
    } catch (IllegalArgumentException e) {
      return FlushMode.COMMIT;
    }
  }

  protected void populateQueryWithNamedArguments(Map<?, ?> namedArgs) {
    if (namedArgs == null) return;
    namedArgs.forEach(
        (key, value) -> {
          if (!(key instanceof CharSequence)) {
            throw new GrailsQueryException("Named parameter's name must be a String: " + namedArgs);
          }
          String name = key.toString();
          if (value == null) {
            delegate.setParameter(name, null);
          } else if (value instanceof Collection<?> col) {
            delegate.setParameterList(name, col);
          } else if (value.getClass().isArray()) {
            delegate.setParameterList(name, (Object[]) value);
          } else if (value instanceof CharSequence cs) {
            delegate.setParameter(name, cs.toString(), String.class);
          } else {
            delegate.setParameter(name, value);
          }
        });
  }

  protected void populateQueryWithIndexedArguments(List<?> params) {
    if (params == null) return;
    for (int i = 0; i < params.size(); i++) {
      Object val = params.get(i);
      if (val instanceof CharSequence cs) delegate.setParameter(i + 1, cs.toString(), String.class);
      else delegate.setParameter(i + 1, val);
    }

  /**
   * Returns the underlying {@link org.hibernate.query.Query} for SELECT queries, or {@code null}
   * for mutation queries.
   */
  public org.hibernate.query.Query<?> getQuery() {
    return delegate.selectQuery();
  }

  /**
   * Returns the underlying {@link org.hibernate.query.Query} for SELECT queries, or {@code null}
   * for mutation queries.
   */
  public org.hibernate.query.Query<?> selectQuery() {
    return delegate.selectQuery();
  }

  public int executeUpdate() {
    return delegate.executeUpdate();
  }

    /**
     * Returns the underlying {@link org.hibernate.query.Query} for SELECT queries, or {@code null}
     * for mutation queries.
     */
    public org.hibernate.query.Query<?> selectQuery() {
        return delegate.selectQuery();
    }



  private static int toInt(Object v, ConversionService cs) {
    if (v instanceof Integer i) return i;
    return cs.convert(v, Integer.class);
  }

  private static boolean toBool(Object v) {
    return Boolean.parseBoolean(v.toString());
  }

  private static boolean toBoolFromMap(Map<?, ?> map, String key) {
    Object v = map.get(key);
    return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
  }

  private static void ifPresent(
      Map<?, ?> map, String key, java.util.function.Consumer<Object> action) {
    Object v = map.get(key);
    if (v != null) action.accept(v);
  }
}
