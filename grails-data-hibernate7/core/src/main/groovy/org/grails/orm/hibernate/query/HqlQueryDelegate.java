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

import java.util.Collection;
import java.util.List;
import org.hibernate.query.QueryFlushMode;

/**
 * Abstracts over Hibernate's {@link org.hibernate.query.Query} (SELECT) and
 * {@link org.hibernate.query.MutationQuery} (UPDATE/DELETE). The two types are
 * siblings under {@link org.hibernate.query.CommonQueryContract} and cannot be held
 * in a single typed field, so {@link HibernateHqlQuery} delegates all query
 * operations through this interface instead.
 *
 * <p>Select-only methods ({@link #setMaxResults}, {@link #setCacheable}, etc.) are
 * no-ops by default; {@link SelectQueryDelegate} overrides them. Mutation-only
 * operations ({@link #executeUpdate}) throw {@link UnsupportedOperationException}
 * in {@link SelectQueryDelegate} and vice-versa for {@link #list()} in
 * {@link MutationQueryDelegate}.
 */
interface HqlQueryDelegate {

  // ── common ────────────────────────────────────────────────────────────────

  void setTimeout(int timeout);

  void setQueryFlushMode(QueryFlushMode mode);

  void setParameter(String name, Object value);

  <T> void setParameter(String name, T value, Class<T> type);

  void setParameter(int position, Object value);

  <T> void setParameter(int position, T value, Class<T> type);

  // ── select-only (no-ops for mutation queries) ─────────────────────────────

  default void setMaxResults(int n) {}

  default void setFirstResult(int n) {}

  default void setCacheable(boolean b) {}

  default void setFetchSize(int n) {}

  default void setReadOnly(boolean b) {}

  /** Sets a named collection parameter. For mutation queries, falls back to {@link #setParameter}. */
  default void setParameterList(String name, Collection<?> values) {}

  /** Sets a named array parameter. For mutation queries, falls back to {@link #setParameter}. */
  default void setParameterList(String name, Object[] values) {}

  // ── execution ─────────────────────────────────────────────────────────────

  /** Returns all results. Throws {@link UnsupportedOperationException} for mutation queries. */
  @SuppressWarnings("rawtypes")
  List list();

  /** Executes an UPDATE/DELETE. Throws {@link UnsupportedOperationException} for SELECT queries. */
  int executeUpdate();

  /**
   * Returns the underlying {@link org.hibernate.query.Query} for SELECT queries, or {@code null}
   * for mutation queries (used by {@link org.grails.orm.hibernate.GrailsHibernateTemplate#applySettings}).
   */
  org.hibernate.query.Query<?> selectQuery();
}
