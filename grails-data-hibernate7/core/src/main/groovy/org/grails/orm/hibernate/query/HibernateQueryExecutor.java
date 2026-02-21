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

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.hibernate.FlushMode;
import org.hibernate.NonUniqueResultException;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.query.criteria.JpaCriteriaQuery;

public record HibernateQueryExecutor(
    Integer offset,
    Integer maxResults,
    LockModeType lockResult,
    Boolean queryCache,
    Integer fetchSize,
    Integer timeout,
    FlushMode flushMode,
    Boolean readOnly,
    ProxyHandler proxyHandler) {

  public List list(Session session, JpaCriteriaQuery jpaCq) {
    return configureQuery(session, jpaCq).getResultList();
  }

  public Object scroll(Session session, JpaCriteriaQuery jpaCq) {
    return configureQuery(session, jpaCq).scroll();
  }

  public Object singleResult(Session session, JpaCriteriaQuery jpaCq) {
    var query = configureQuery(session, jpaCq);
    try {

      Object singleResult = query.getSingleResult();
      return proxyHandler.unwrap(singleResult);
    } catch (NonUniqueResultException e) {
      return proxyHandler.unwrap(query.getResultList().get(0));
    } catch (jakarta.persistence.NoResultException e) {
      return null;
    }
  }

  private Query configureQuery(Session session, JpaCriteriaQuery jpaCq) {
    var query = session.createQuery(jpaCq);
    if (jakarta.persistence.Tuple.class.equals(jpaCq.getResultType())) {
      query.setTupleTransformer((payload, aliases) -> payload);
    }
    Optional.ofNullable(offset).filter(v -> v > 0).ifPresent(query::setFirstResult);
    Optional.ofNullable(queryCache).ifPresent(qc -> query.setHint("org.hibernate.cacheable", qc));
    Optional.ofNullable(maxResults).filter(v -> v > 0).ifPresent(query::setMaxResults);
    Optional.ofNullable(lockResult).ifPresent(query::setLockMode);
    Optional.ofNullable(fetchSize).filter(v -> v > 0).ifPresent(query::setFetchSize);
    Optional.ofNullable(timeout).filter(v -> v > 0).ifPresent(query::setTimeout);
    Optional.ofNullable(flushMode)
        .map(mode -> mode.toJpaFlushMode())
        .ifPresent(query::setFlushMode);
    Optional.ofNullable(readOnly).ifPresent(query::setReadOnly);
    return query;
  }
}
