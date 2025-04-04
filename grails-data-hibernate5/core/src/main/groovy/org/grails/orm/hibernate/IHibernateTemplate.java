/*
 * Copyright 2013 SpringSource.
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
package org.grails.orm.hibernate;

import java.io.Serializable;
import java.util.Collection;

import groovy.lang.Closure;
import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

/**
 * Template interface that can be used with both Hibernate 3 and Hibernate 4
 *
 * @author Burt Beckwith
 * @author Graeme Rocher
 */
public interface IHibernateTemplate {

    Serializable save(Object o);

    void refresh(Object o);

    void lock(Object o, LockMode lockMode);

    void flush();

    void clear();

    void evict(Object o);

    boolean contains(Object o);

    void setFlushMode(int mode);

    int getFlushMode();

    void deleteAll(Collection<?> list);

    void applySettings(Query query);

    void applySettings(Criteria criteria);

    <T> T get(Class<T> type, Serializable key);

    <T> T get(Class<T> type, Serializable key, LockMode mode);

    <T> T load(Class<T> type, Serializable key);

    void delete(Object o);

    SessionFactory getSessionFactory();

    <T> T execute(Closure<T> callable);

    <T> T executeWithNewSession(Closure<T> callable);

    <T1> T1 executeWithExistingOrCreateNewSession(SessionFactory sessionFactory, Closure<T1> callable);
}
