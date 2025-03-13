/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.orm;

import groovy.lang.GroovySystem;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.query.*;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.JoinType;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.List;
import java.util.Map;

/**
 * <p>Wraps the Hibernate Criteria API in a builder. The builder can be retrieved through the "createCriteria()" dynamic static
 * method of Grails domain classes (Example in Groovy):
 * <pre>
 *         def c = Account.createCriteria()
 *         def results = c {
 *             projections {
 *                 groupProperty("branch")
 *             }
 *             like("holderFirstName", "Fred%")
 *             and {
 *                 between("balance", 500, 1000)
 *                 eq("branch", "London")
 *             }
 *             maxResults(10)
 *             order("holderLastName", "desc")
 *         }
 * </pre>
 * <p>The builder can also be instantiated standalone with a SessionFactory and persistent Class instance:
 * <pre>
 *      new HibernateCriteriaBuilder(clazz, sessionFactory).list {
 *         eq("firstName", "Fred")
 *      }
 * </pre>
 *
 * @author Graeme Rocher
 */
public class HibernateCriteriaBuilder extends AbstractHibernateCriteriaBuilder {
    /*
     * Define constants which may be used inside of criteria queries
     * to refer to standard Hibernate Type instances.
     */
    public static final Type BOOLEAN = StandardBasicTypes.BOOLEAN;
    public static final Type YES_NO = StandardBasicTypes.YES_NO;
    public static final Type BYTE = StandardBasicTypes.BYTE;
    public static final Type CHARACTER = StandardBasicTypes.CHARACTER;
    public static final Type SHORT = StandardBasicTypes.SHORT;
    public static final Type INTEGER = StandardBasicTypes.INTEGER;
    public static final Type LONG = StandardBasicTypes.LONG;
    public static final Type FLOAT = StandardBasicTypes.FLOAT;
    public static final Type DOUBLE = StandardBasicTypes.DOUBLE;
    public static final Type BIG_DECIMAL = StandardBasicTypes.BIG_DECIMAL;
    public static final Type BIG_INTEGER = StandardBasicTypes.BIG_INTEGER;
    public static final Type STRING = StandardBasicTypes.STRING;
    public static final Type NUMERIC_BOOLEAN = StandardBasicTypes.NUMERIC_BOOLEAN;
    public static final Type TRUE_FALSE = StandardBasicTypes.TRUE_FALSE;
    public static final Type URL = StandardBasicTypes.URL;
    public static final Type TIME = StandardBasicTypes.TIME;
    public static final Type DATE = StandardBasicTypes.DATE;
    public static final Type TIMESTAMP = StandardBasicTypes.TIMESTAMP;
    public static final Type CALENDAR = StandardBasicTypes.CALENDAR;
    public static final Type CALENDAR_DATE = StandardBasicTypes.CALENDAR_DATE;
    public static final Type CLASS = StandardBasicTypes.CLASS;
    public static final Type LOCALE = StandardBasicTypes.LOCALE;
    public static final Type CURRENCY = StandardBasicTypes.CURRENCY;
    public static final Type TIMEZONE = StandardBasicTypes.TIMEZONE;
    public static final Type UUID_BINARY = StandardBasicTypes.UUID_BINARY;
    public static final Type UUID_CHAR = StandardBasicTypes.UUID_CHAR;
    public static final Type BINARY = StandardBasicTypes.BINARY;
    public static final Type WRAPPER_BINARY = StandardBasicTypes.WRAPPER_BINARY;
    public static final Type IMAGE = StandardBasicTypes.IMAGE;
    public static final Type BLOB = StandardBasicTypes.BLOB;
    public static final Type MATERIALIZED_BLOB = StandardBasicTypes.MATERIALIZED_BLOB;
    public static final Type CHAR_ARRAY = StandardBasicTypes.CHAR_ARRAY;
    public static final Type CHARACTER_ARRAY = StandardBasicTypes.CHARACTER_ARRAY;
    public static final Type TEXT = StandardBasicTypes.TEXT;
    public static final Type CLOB = StandardBasicTypes.CLOB;
    public static final Type MATERIALIZED_CLOB = StandardBasicTypes.MATERIALIZED_CLOB;
    public static final Type SERIALIZABLE = StandardBasicTypes.SERIALIZABLE;

    @SuppressWarnings("rawtypes")
    public HibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory) {
        super(targetClass, sessionFactory);
        setDefaultFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
    }

    @SuppressWarnings("rawtypes")
    public HibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory, boolean uniqueResult) {
        super(targetClass, sessionFactory, uniqueResult);
        setDefaultFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
    }

    /**
     * Join an association using the specified join-type, assigning an alias
     * to the joined association.
     * The joinType is expected to be one of CriteriaSpecification.INNER_JOIN (the default),
     * CriteriaSpecificationFULL_JOIN, or CriteriaSpecificationLEFT_JOIN.
     *
     * @param associationPath A dot-seperated property path
     * @param alias           The alias to assign to the joined association (for later reference).
     * @param joinType        The type of join to use.
     * @return this (for method chaining)
     * @throws HibernateException Indicates a problem creating the sub criteria
     * @see #createAlias(String, String)
     */
    public Criteria createAlias(String associationPath, String alias, int joinType) {
        return criteria.createAlias(associationPath, alias, JoinType.parse(joinType));
    }

    @Override
    protected Object executeUniqueResultWithProxyUnwrap() {
        return GrailsHibernateUtil.unwrapIfProxy(criteria.uniqueResult());
    }

    @Override
    protected void cacheCriteriaMapping() {
        GrailsHibernateUtil.cacheCriteriaByMapping(datastore, targetClass, criteria);
    }

    protected Class getClassForAssociationType(Attribute<?, ?> type) {
        if (type instanceof PluralAttribute) {
            return ((PluralAttribute)type).getElementType().getJavaType();
        }
        return type.getJavaType();
    }

    @Override
    protected List createPagedResultList(Map args) {
        GrailsHibernateUtil.populateArgumentsForCriteria(datastore, targetClass, criteria, args, conversionService);
        GrailsHibernateTemplate ght = new GrailsHibernateTemplate(sessionFactory, (HibernateDatastore) datastore, getDefaultFlushMode());
        return new PagedResultList(ght, criteria);
    }

    /**
     * Creates a Criterion with from the specified property name and "rlike" (a regular expression version of "like") expression
     *
     * @param propertyName  The property name
     * @param propertyValue The ilike value
     * @return A Criterion instance
     */
    public org.grails.datastore.mapping.query.api.Criteria rlike(String propertyName, Object propertyValue) {
        if (!validateSimpleExpression()) {
            throwRuntimeException(new IllegalArgumentException("Call to [rlike] with propertyName [" +
                    propertyName + "] and value [" + propertyValue + "] not allowed here."));
        }

        propertyName = calculatePropertyName(propertyName);
        propertyValue = calculatePropertyValue(propertyValue);
        addToCriteria(new RlikeExpression(propertyName, propertyValue));
        return this;
    }

    @Override
    protected void createCriteriaInstance() {
        {
            if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
                participate = true;
                hibernateSession = ((SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory)).getSession();
            }
            else {
                hibernateSession = sessionFactory.openSession();
            }

            criteria = hibernateSession.createCriteria(targetClass);
            cacheCriteriaMapping();
            criteriaMetaClass = GroovySystem.getMetaClassRegistry().getMetaClass(criteria.getClass());
        }
    }

    @Override
    protected org.hibernate.criterion.DetachedCriteria convertToHibernateCriteria(QueryableCriteria<?> queryableCriteria) {
        return getHibernateDetachedCriteria(new HibernateQuery(criteria, queryableCriteria.getPersistentEntity()), queryableCriteria);
    }

    public static org.hibernate.criterion.DetachedCriteria getHibernateDetachedCriteria(AbstractHibernateQuery query, QueryableCriteria<?> queryableCriteria) {
        String alias = queryableCriteria.getAlias();
        return getHibernateDetachedCriteria(query, queryableCriteria, alias);
    }

    public static org.hibernate.criterion.DetachedCriteria getHibernateDetachedCriteria(AbstractHibernateQuery query, QueryableCriteria<?> queryableCriteria, String alias) {
        PersistentEntity persistentEntity = queryableCriteria.getPersistentEntity();
        Class targetClass = persistentEntity.getJavaClass();
        org.hibernate.criterion.DetachedCriteria detachedCriteria;

        if(alias != null) {
            detachedCriteria = org.hibernate.criterion.DetachedCriteria.forClass(targetClass, alias);
        }
        else {
            detachedCriteria = org.hibernate.criterion.DetachedCriteria.forClass(targetClass);
        }
        populateHibernateDetachedCriteria(new HibernateQuery(detachedCriteria,persistentEntity), detachedCriteria, queryableCriteria);
        return detachedCriteria;
    }

    private static void populateHibernateDetachedCriteria(AbstractHibernateQuery query, org.hibernate.criterion.DetachedCriteria detachedCriteria, QueryableCriteria<?> queryableCriteria) {
        List<org.grails.datastore.mapping.query.Query.Criterion> criteriaList = queryableCriteria.getCriteria();
        for (org.grails.datastore.mapping.query.Query.Criterion criterion : criteriaList) {
            Criterion hibernateCriterion = HibernateQuery.HIBERNATE_CRITERION_ADAPTER.toHibernateCriterion(query, criterion, null);
            if (hibernateCriterion != null) {
                detachedCriteria.add(hibernateCriterion);
            }
        }

        List<org.grails.datastore.mapping.query.Query.Projection> projections = queryableCriteria.getProjections();
        ProjectionList projectionList = Projections.projectionList();
        for (org.grails.datastore.mapping.query.Query.Projection projection : projections) {
            Projection hibernateProjection = new HibernateProjectionAdapter(projection).toHibernateProjection();
            if (hibernateProjection != null) {
                projectionList.add(hibernateProjection);
            }
        }
        detachedCriteria.setProjection(projectionList);
    }

    /**
     * Closes the session if it is copen
     */
    @Override
    protected void closeSession() {
        if (hibernateSession != null && hibernateSession.isOpen() && !participate) {
            hibernateSession.close();
        }
    }

}
