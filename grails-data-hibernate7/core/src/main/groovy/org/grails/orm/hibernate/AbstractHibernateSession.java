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
package org.grails.orm.hibernate;

import jakarta.persistence.FlushModeType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.grails.datastore.mapping.core.AbstractAttributeStoringSession;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.api.QueryAliasAwareSession;
import org.grails.datastore.mapping.transactions.Transaction;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Session implementation that wraps a Hibernate {@link Session}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractHibernateSession extends AbstractAttributeStoringSession
    implements QueryAliasAwareSession {

  protected AbstractHibernateDatastore datastore;
  protected boolean connected = true;
  protected IHibernateTemplate hibernateTemplate;

  protected AbstractHibernateSession(
      AbstractHibernateDatastore hibernateDatastore, SessionFactory sessionFactory) {
    datastore = hibernateDatastore;
  }

  @Override
  public boolean isSchemaless() {
    return false;
  }

  public Serializable insert(Object o) {
    return persist(o);
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  @Override
  public void disconnect() {
    connected = false; // don't actually do any disconnection here. This will be handled by OSVI
  }

  public Transaction beginTransaction() {
    throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
  }

  @Override
  public Transaction beginTransaction(TransactionDefinition definition) {
    throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
  }

  public MappingContext getMappingContext() {
    return getDatastore().getMappingContext();
  }

  public Serializable persist(Object o) {
    hibernateTemplate.persist(o);
    try {
      // Try to obtain identifier via mapping context reflectors
      MappingContext ctx = getDatastore().getMappingContext();
      org.grails.datastore.mapping.model.PersistentEntity pe =
          ctx.getPersistentEntity(o.getClass().getName());
      if (pe != null) {
        Object id = ctx.getEntityReflector(pe).getIdentifier(o);
        return id == null ? null : (Serializable) id;
      }
    } catch (Exception e) {
      // ignore and return null when identifier cannot be obtained
    }
    return null;
  }

  /**
   * Merge the given instance state into the current persistence context and return the managed
   * instance.
   */
  public Object merge(Object o) {
    return hibernateTemplate.merge(o);
  }

  public void refresh(Object o) {
    hibernateTemplate.refresh(o);
  }

  public void attach(Object o) {
    hibernateTemplate.lock(o, LockMode.NONE);
  }

  public void flush() {
    hibernateTemplate.flush();
  }

  public void clear() {
    hibernateTemplate.clear();
  }

  public void clear(Object o) {
    hibernateTemplate.evict(o);
  }

  public boolean contains(Object o) {
    return hibernateTemplate.contains(o);
  }

  public void lock(Object o) {
    hibernateTemplate.lock(o, LockMode.PESSIMISTIC_WRITE);
  }

  public void unlock(Object o) {
    // do nothing
  }

  /**
   * @deprecated persist method needs to be changed to void
   * @param objects The Objects
   * @return
   */
  @Deprecated()
  public List<Serializable> persist(Iterable objects) {
    List<Serializable> ids = new ArrayList<>();
    for (Object object : objects) {
      Serializable id = persist(object);
      ids.add(id);
    }
    return ids;
  }

  public <T> T retrieve(Class<T> type, Serializable key) {
    return hibernateTemplate.get(type, key);
  }

  public <T> T proxy(Class<T> type, Serializable key) {
    return hibernateTemplate.load(type, key);
  }

  public <T> T lock(Class<T> type, Serializable key) {
    return hibernateTemplate.get(type, key, LockMode.PESSIMISTIC_WRITE);
  }

  public void delete(Iterable objects) {
    Collection list = getIterableAsCollection(objects);
    hibernateTemplate.deleteAll(list);
  }

  @SuppressWarnings("unchecked")
  protected Collection getIterableAsCollection(Iterable objects) {
    Collection list;
    if (objects instanceof Collection) {
      list = (Collection) objects;
    } else {
      list = new ArrayList();
      for (Object object : objects) {
        list.add(object);
      }
    }
    return list;
  }

  public void delete(Object obj) {
    hibernateTemplate.remove(obj);
  }

  public List retrieveAll(Class type, Serializable... keys) {
    return retrieveAll(type, Arrays.asList(keys));
  }

  public Persister getPersister(Object o) {
    return null;
  }

  public Transaction getTransaction() {
    throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
  }

  @Override
  public boolean hasTransaction() {
    Object resource =
        TransactionSynchronizationManager.getResource(hibernateTemplate.getSessionFactory());
    return resource != null;
  }

  public Datastore getDatastore() {
    return datastore;
  }

  public boolean isDirty(Object o) {
    // not used, Hibernate manages dirty checking itself
    return true;
  }

  public Object getNativeInterface() {
    return hibernateTemplate;
  }

  @Override
  public void setSynchronizedWithTransaction(boolean synchronizedWithTransaction) {
    // no-op
  }

  public abstract FlushModeType getFlushMode();
}
