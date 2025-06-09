/*
 * Copyright (C) 2011 SpringSource
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



import grails.gorm.DetachedCriteria;
import groovy.lang.Closure;
import org.grails.datastore.mapping.query.Projections;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.Restrictions;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.AbstractHibernateSession;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.PropertyMapping;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class HibernateQuery extends AbstractHibernateQuery {

    public HibernateQuery(AbstractHibernateSession session, PersistentEntity entity) {
        super(session, entity);
    }

    protected PropertyMapping getEntityPersister(String name, SessionFactory sessionFactory) {
        return (PropertyMapping) ((SharedSessionContractImplementor) sessionFactory).getEntityPersister(name,this.entity);
    }

    /**
     * TODO FIX THIS
     * @return The hibernate criteria
     */
    public DetachedCriteria getHibernateCriteria() {
        return detachedCriteria;
    }

    public Query notIn(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.notIn(propertyName,subquery);
        return this;
    }

    public Query exists(QueryableCriteria<?> subquery) {
        detachedCriteria.exists(subquery);
        return this;
    }

    public Query notExits( QueryableCriteria<?> subquery) {
        detachedCriteria.notExists(subquery);
        return this;
    }


    public Query gtAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.gtAll(propertyName,subquery);
        return this;
    }

    public Query geAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.geAll(propertyName,subquery);
        return this;
    }

    public Query ltAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.ltAll(propertyName,subquery);
        return this;
    }

    public Query leAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.leAll(propertyName,subquery);
        return this;
    }

    public Query gtSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.gtSome(propertyName,subquery);
        return this;
    }

    public Query geSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.geSome(propertyName,subquery);
        return this;
    }

    public Query ltSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.ltSome(propertyName,subquery);
        return this;
    }

    public Query leSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.leSome(propertyName,subquery);
        return this;
    }

    public Query eqAll(String propertyName, QueryableCriteria propertyValue){
        detachedCriteria.eqAll(propertyName,propertyValue);
        return this;
    }

    public Query and(Closure closure) {
        detachedCriteria.and(closure);
        return this;
    }

    public Query or(Closure closure) {
        detachedCriteria.or(closure);
        return this;
    }

    public Query not(Criterion a, Criterion b) {
        Closure addClosure = new Closure(this) {
            public void doCall() {
                DetachedCriteria owner = (DetachedCriteria) getDelegate();
                owner.add(Restrictions.or(a,b));
            }
        };
        detachedCriteria.not(addClosure);
        return this;
    }

    public Query not(Closure closure) {
        detachedCriteria.not(closure);
        return this;
    }

    public Query ne(String propertyName, Object propertyValue) {
        detachedCriteria.ne(propertyName, propertyValue);
        return this;
    }

    public Query eqProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.eqProperty(propertyName,otherPropertyName);
        return this;
    }

    public Query neProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.neProperty(propertyName,otherPropertyName);
        return this;
    }

    public Query gtProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.gtProperty(propertyName,otherPropertyName);
        return this;
    }


    public Query geProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.geProperty(propertyName,otherPropertyName);
        return this;
    }

    public Query ltProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.ltProperty(propertyName,otherPropertyName);
        return this;
    }

    public Query leProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.leProperty(propertyName,otherPropertyName);
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


    public Object scroll() {
        return createQuery().scroll();
    }

    public Query distinct() {
        projections.add(Projections.distinct());
        return this;
    }





    //TODO verify this is complete
    @Override
    public Object clone() {
        final HibernateSession hibernateSession = (HibernateSession) getSession();
        final GrailsHibernateTemplate hibernateTemplate = (GrailsHibernateTemplate) hibernateSession.getNativeInterface();
        return hibernateTemplate.execute((GrailsHibernateTemplate.HibernateCallback<Object>) session -> {
            HibernateQuery hibernateQuery = new HibernateQuery(hibernateSession, entity);
            hibernateQuery.max(this.max);
            hibernateQuery.offset(this.offset);
            hibernateQuery.setDetachedCriteria(this.detachedCriteria);
            this.projections.getProjectionList().forEach(projection -> {hibernateQuery.projections().add(projection);});;
            getCriteria().getCriteria().forEach(hibernateQuery::add);
            return hibernateQuery;
        });
    }

}
