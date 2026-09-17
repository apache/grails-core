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
package grails.orm

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.PluralAttribute

import org.hibernate.FetchMode
import org.hibernate.SessionFactory

import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.gorm.DetachedCriteria
import grails.gorm.MultiTenant
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.mapping.query.api.ProjectionList
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.orm.hibernate.GrailsHibernateTemplate
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.HibernateSession
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.support.hibernate7.SessionHolder

/**
 * Implements the GORM criteria DSL for Hibernate 7+. The builder exposes a Groovy-closure DSL that
 * is translated into JPA Criteria queries via {@link HibernateQuery}. It is the backing
 * implementation for the {@code createCriteria()} and {@code withCriteria()} dynamic static methods
 * that GORM adds to every domain class.
 *
 * <h2>DSL usage via domain class</h2>
 *
 * <pre>
 *         def c = Account.createCriteria()
 *         def results = c.list {
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
 *             cache(true)
 *             readOnly(true)
 *         }
 * </pre>
 *
 * <h2>Advanced Features</h2>
 *
 * <p>The builder supports several advanced Hibernate features:
 *
 * <ul>
 *   <li><b>Pessimistic Locking:</b> Use {@code lock(true)} to obtain a pessimistic write lock.
 *   <li><b>Query Caching:</b> Use {@code cache(true)} to enable query caching for the results.
 *   <li><b>Read-Only Mode:</b> Use {@code readOnly(true)} to disable dirty checking for loaded
 *       entities.
 *   <li><b>Fetch Mode:</b> Use {@code fetchMode("association", FetchMode.JOIN)} to specify Eager/Lazy
 *       fetching strategies.
 * </ul>
 *
 * <h2>Programmatic instantiation</h2>
 *
 * <p>The builder requires a {@link SessionFactory}, the target persistent class, and the {@link
 * org.grails.orm.hibernate.HibernateDatastore} that owns the session:
 *
 * <pre>
 *      new HibernateCriteriaBuilder(Account, sessionFactory, datastore).list {
 *         eq("firstName", "Fred")
 *      }
 * </pre>
 *
 * <h2>Architecture</h2>
 *
 * <p>Closure method calls in the DSL are dispatched through {@code invokeMethod} → {@code
 * CriteriaMethodInvoker} → {@link HibernateQuery}, which translates each GORM constraint into the
 * equivalent JPA Criteria predicate. {@link grails.gorm.DetachedCriteria} can also be passed in
 * place of a closure to support multi-tenant and reusable query fragments.
 *
 * To customise which methods are handled, either extend {@link CriteriaMethodInvoker} and inject it
 * via {@link #setCriteriaMethodInvoker(CriteriaMethodInvoker)}, or subclass this class directly.
 *
 * @author Graeme Rocher
 * @see HibernateQuery
 * @see grails.gorm.DetachedCriteria
 */
@Slf4j
@SuppressWarnings('PMD.AvoidDuplicateLiterals')
@CompileStatic
class HibernateCriteriaBuilder extends GroovyObjectSupport implements BuildableCriteria, ProjectionList {

    private final SessionFactory sessionFactory
    private final boolean participate
    private final org.hibernate.query.criteria.HibernateCriteriaBuilder cb
    private final HibernateQuery hibernateQuery
    private Class<?> targetClass
    private CriteriaQuery<?> criteriaQuery
    private boolean uniqueResult = false

    @SuppressWarnings('PMD.AvoidFieldNameMatchingMethodName')
    private boolean scroll

    @SuppressWarnings('PMD.AvoidFieldNameMatchingMethodName')
    private boolean count

    private boolean paginationEnabledList = false
    private int defaultFlushMode

    @SuppressWarnings('PMD.AvoidFieldNameMatchingMethodName')
    private boolean distinct = false
    private CriteriaMethodInvoker criteriaMethodInvoker

    @SuppressWarnings(['rawtypes', 'PMD.CloseResource'])
    HibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory, HibernateDatastore datastore) {
        this.targetClass = targetClass
        setDatastore(datastore)
        this.sessionFactory = sessionFactory
        this.cb = sessionFactory.getCriteriaBuilder()
        if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
            this.participate = true
        }
        else {
            this.participate = false
            org.hibernate.Session session = sessionFactory.openSession()
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))
        }
        HibernateSession session = (HibernateSession) datastore.connect()
        hibernateQuery = new HibernateQuery(
                session, (GrailsHibernatePersistentEntity) datastore.getMappingContext().getPersistentEntity(targetClass.getName()))
        setDefaultFlushMode(GrailsHibernateTemplate.FLUSH_AUTO)
        criteriaMethodInvoker = new CriteriaMethodInvoker(this)
    }

    public static final String ALIAS_SEPARATOR = ':'

    private static String getFullyQualifiedColumn(String propertyName, String alias) {
        return (Objects.nonNull(alias) ? alias + ALIAS_SEPARATOR : '') + propertyName
    }

    org.grails.datastore.mapping.query.api.Criteria exists(Closure subquery) {
        return exists(new grails.gorm.DetachedCriteria(targetClass).build(subquery))
    }

    org.grails.datastore.mapping.query.api.Criteria notExists(Closure subquery) {
        return notExists(new grails.gorm.DetachedCriteria(targetClass).build(subquery))
    }

    final void setDatastore(HibernateDatastore datastore) {
        if (MultiTenant.isAssignableFrom(targetClass) &&
                datastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            datastore.enableMultiTenancyFilter()
        }
    }

    org.grails.datastore.mapping.query.api.Criteria createAlias(String associationPath, String alias) {
        def prop = hibernateQuery.getEntity().getPropertyByName(associationPath)
        if (prop instanceof org.grails.datastore.mapping.model.types.Basic) {
            hibernateQuery.addAlias(
                    new org.grails.orm.hibernate.query.HibernateAlias(associationPath, alias, JoinType.INNER))
            return this
        }
        hibernateQuery.getDetachedCriteria().createAlias(associationPath, alias)
        hibernateQuery.getDetachedCriteria().join(associationPath, JoinType.INNER)
        return this
    }

    org.grails.datastore.mapping.query.api.Criteria createAlias(
            String associationPath, String alias, int joinType) {
        def prop = hibernateQuery.getEntity().getPropertyByName(associationPath)
        JoinType convertedJoinType = convertFromInt(joinType)
        if (prop instanceof org.grails.datastore.mapping.model.types.Basic) {
            hibernateQuery.addAlias(
                    new org.grails.orm.hibernate.query.HibernateAlias(associationPath, alias, convertedJoinType))
            return this
        }
        hibernateQuery.getDetachedCriteria().createAlias(associationPath, alias)
        hibernateQuery.getDetachedCriteria().join(associationPath, convertedJoinType)
        return this
    }

    /**
     * A projection that selects a property name
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList property(String propertyName) {
        hibernateQuery.projections().property(propertyName)
        return this
    }

    Query.ProjectionList projections() {
        return hibernateQuery.projections()
    }

    /**
     * A projection that selects a distince property name
     *
     * @param propertyName The property name
     */
    @Override
    ProjectionList distinct(String propertyName) {
        hibernateQuery.projections().distinct(propertyName)
        return this
    }

    /**
     * Adds a projection that allows the criteria to return the property average value
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList avg(String propertyName) {
        hibernateQuery.projections().avg(propertyName)
        return this
    }

    /**
     * Use a join query
     *
     * @param associationPath The path of the association
     */
    @Override
    BuildableCriteria join(String associationPath) {
        join(associationPath, JoinType.INNER)
        return this
    }

    @Override
    BuildableCriteria join(String property, JoinType joinType) {
        hibernateQuery.join(property, joinType)
        return this
    }

    /**
     * Whether a pessimistic lock should be obtained.
     *
     * @param shouldLock True if it should
     */
    void lock(boolean shouldLock) {
        hibernateQuery.lock(shouldLock)
    }

    /**
     * Use a select query
     *
     * @param associationPath The path of the association
     */
    @Override
    BuildableCriteria select(String associationPath) {
        hibernateQuery.select(associationPath)
        return this
    }

    /**
     * Whether to use the query cache
     *
     * @param shouldCache True if the query should be cached
     */
    @Override
    BuildableCriteria cache(boolean shouldCache) {
        hibernateQuery.cache(shouldCache)
        return this
    }

    BuildableCriteria maxResults(int max) {
        hibernateQuery.maxResults(max)
        return this
    }

    /**
     * Whether to check for changes on the objects loaded
     *
     * @param readOnly True to disable dirty checking
     */
    @Override
    BuildableCriteria readOnly(boolean readOnly) {
        hibernateQuery.setReadOnly(readOnly)
        return this
    }

    @Override
    Class<?> getTargetClass() {
        return targetClass
    }

    void setTargetClass(Class<?> targetClass) {
        this.targetClass = targetClass
    }

    /**
     * Adds a projection that allows the criteria to return the property count
     *
     * @param propertyName The name of the property
     */
    void count(String propertyName) {
        count(propertyName, null)
    }

    /**
     * Adds a projection that allows the criteria to return the property count
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    void count(String propertyName, String alias) {
        hibernateQuery.projections().add(new org.grails.orm.hibernate.query.Hibernate7CountProjection(getFullyQualifiedColumn(propertyName, alias)))
    }

    @Override
    ProjectionList id() {
        hibernateQuery.projections().id()
        return this
    }

    @Override
    ProjectionList count() {
        return hibernateQuery.projections().count()
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList countDistinct(String propertyName) {
        return countDistinct(propertyName, null)
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList groupProperty(String propertyName) {
        return groupProperty(propertyName, null)
    }

    @Override
    ProjectionList distinct() {
        hibernateQuery.projections().distinct()
        return this
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    ProjectionList countDistinct(String propertyName, String alias) {
        hibernateQuery.projections().countDistinct(getFullyQualifiedColumn(propertyName, alias))
        return this
    }

    /**
     * Adds a projection that allows the criteria's result to be grouped by a property
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    ProjectionList groupProperty(String propertyName, String alias) {
        hibernateQuery.projections().groupProperty(getFullyQualifiedColumn(propertyName, alias))
        return this
    }

    /**
     * Adds a projection that allows the criteria to retrieve a maximum property value
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList max(String propertyName) {
        return max(propertyName, null)
    }

    /**
     * Adds a projection that allows the criteria to retrieve a maximum property value
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    ProjectionList max(String propertyName, String alias) {
        hibernateQuery.projections().max(getFullyQualifiedColumn(propertyName, alias))
        return this
    }

    /**
     * Adds a projection that allows the criteria to retrieve a minimum property value
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList min(String propertyName) {
        return min(propertyName, null)
    }

    /**
     * Adds a projection that allows the criteria to retrieve a minimum property value
     *
     * @param alias The alias to use
     */
    ProjectionList min(String propertyName, String alias) {
        hibernateQuery.projections().min(getFullyQualifiedColumn(propertyName, alias))
        return this
    }

    /** Adds a projection that allows the criteria to return the row count */
    @Override
    ProjectionList rowCount() {
        return count()
    }

    /**
     * Adds a projection that allows the criteria to retrieve the sum of the results of a property
     *
     * @param propertyName The name of the property
     */
    @Override
    ProjectionList sum(String propertyName) {
        return sum(propertyName, null)
    }

    /**
     * Adds a projection that allows the criteria to retrieve the sum of the results of a property
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    ProjectionList sum(String propertyName, String alias) {
        hibernateQuery.projections().sum(getFullyQualifiedColumn(propertyName, alias))
        return this
    }

    /**
     * Sets the fetch mode of an associated path
     *
     * @param associationPath The name of the associated path
     * @param fetchMode The fetch mode to set
     */
    void fetchMode(String associationPath, FetchMode fetchMode) {
        if (fetchMode == FetchMode.SELECT) {
            hibernateQuery.getDetachedCriteria().select(associationPath)
        }
        else {
            hibernateQuery.getDetachedCriteria().join(associationPath)
        }
    }

    /**
     * Creates a Criterion that compares to class properties for equality
     *
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    @Override
    Criteria eqProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.eqProperty(propertyName, otherPropertyName)
        return this
    }

    /**
     * Creates a Criterion that compares to class properties for !equality
     *
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    @Override
    Criteria neProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.neProperty(propertyName, otherPropertyName)
        return this
    }

    /**
     * Creates a Criterion that tests if the first property is greater than the second property
     *
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    @Override
    Criteria gtProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.gtProperty(propertyName, otherPropertyName)
        return this
    }

    /**
     * Creates a Criterion that tests if the first property is greater than or equal to the second
     * property
     *
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    @Override
    Criteria geProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.geProperty(propertyName, otherPropertyName)
        return this
    }

    /**
     * Creates a Criterion that tests if the first property is less than the second property
     *
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    @Override
    Criteria ltProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.ltProperty(propertyName, otherPropertyName)
        return this
    }

    /**
     * Creates a Criterion that tests if the first property is less than or equal to the second
     * property
     *
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    @Override
    Criteria leProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.leProperty(propertyName, otherPropertyName)
        return this
    }

    @Override
    Criteria allEq(Map<String, Object> propertyValues) {
        hibernateQuery.allEq(propertyValues)
        return this
    }

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned
     * values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Criteria eqAll(String propertyName, Closure<?> propertyValue) {
        return eqAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue))
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Criteria gtAll(String propertyName, Closure<?> propertyValue) {
        return gtAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue))
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Criteria ltAll(String propertyName, Closure<?> propertyValue) {
        return ltAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue))
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Criteria geAll(String propertyName, Closure<?> propertyValue) {
        return geAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue))
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Criteria leAll(String propertyName, Closure<?> propertyValue) {
        return leAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue))
    }

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned
     * values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria eqAll(String propertyName, @SuppressWarnings('rawtypes') QueryableCriteria propertyValue) {
        hibernateQuery.eqAll(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria gtAll(String propertyName, QueryableCriteria<?> propertyValue) {
        hibernateQuery.gtAll(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria gtSome(String propertyName, QueryableCriteria<?> propertyValue) {
        hibernateQuery.gtSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria gtSome(String propertyName, Closure<?> propertyValue) {
        return gtSome(propertyName, new DetachedCriteria<>(targetClass).build(propertyValue))
    }

    @Override
    Criteria geSome(String propertyName, QueryableCriteria<?> propertyValue) {
        hibernateQuery.geSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria geSome(String propertyName, Closure<?> propertyValue) {
        return geSome(propertyName, new DetachedCriteria<>(targetClass).build(propertyValue))
    }

    @Override
    Criteria ltSome(String propertyName, QueryableCriteria<?> propertyValue) {
        hibernateQuery.ltSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria ltSome(String propertyName, Closure<?> propertyValue) {
        return ltSome(propertyName, new DetachedCriteria<>(targetClass).build(propertyValue))
    }

    @Override
    Criteria leSome(String propertyName, QueryableCriteria<?> propertyValue) {
        hibernateQuery.leSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria leSome(String propertyName, Closure<?> propertyValue) {
        return leSome(propertyName, new DetachedCriteria<>(targetClass).build(propertyValue))
    }

    @Override
    Criteria in(String propertyName, QueryableCriteria<?> subquery) {
        return inList(propertyName, subquery)
    }

    @Override
    Criteria inList(String propertyName, QueryableCriteria<?> subquery) {
        hibernateQuery.in(propertyName, subquery)
        return this
    }

    @Override
    Criteria in(String propertyName, Closure<?> subquery) {
        return inList(propertyName, new DetachedCriteria<>(targetClass).build(subquery))
    }

    @Override
    Criteria inList(String propertyName, Closure<?> subquery) {
        return inList(propertyName, new DetachedCriteria<>(targetClass).build(subquery))
    }

    @Override
    Criteria notIn(String propertyName, QueryableCriteria<?> subquery) {
        hibernateQuery.notIn(propertyName, subquery)
        return this
    }

    @Override
    Criteria notIn(String propertyName, Closure<?> subquery) {
        return notIn(propertyName, new DetachedCriteria<>(targetClass).build(subquery))
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria ltAll(String propertyName, @SuppressWarnings('rawtypes') QueryableCriteria propertyValue) {
        hibernateQuery.ltAll(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria geAll(String propertyName, @SuppressWarnings('rawtypes') QueryableCriteria propertyValue) {
        hibernateQuery.geAll(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given
     * returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria leAll(String propertyName, @SuppressWarnings('rawtypes') QueryableCriteria propertyValue) {
        hibernateQuery.leAll(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a "greater than" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria gt(String propertyName, Object propertyValue) {
        hibernateQuery.gt(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria lte(String s, Object o) {
        return le(s, o)
    }

    /**
     * Creates a "greater than or equal to" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria ge(String propertyName, Object propertyValue) {
        hibernateQuery.ge(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a "less than" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria lt(String propertyName, Object propertyValue) {
        hibernateQuery.lt(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a "less than or equal to" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria le(String propertyName, Object propertyValue) {
        hibernateQuery.le(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria idEquals(Object o) {
        return idEq(o)
    }

    @Override
    Criteria exists(QueryableCriteria<?> subquery) {
        hibernateQuery.exists(subquery)
        return this
    }

    @Override
    Criteria notExists(QueryableCriteria<?> subquery) {
        hibernateQuery.notExits(subquery)
        return this
    }

    @Override
    Criteria isEmpty(String property) {
        hibernateQuery.isEmpty(property)
        return this
    }

    @Override
    Criteria isNotEmpty(String property) {
        hibernateQuery.isNotEmpty(property)
        return this
    }

    @Override
    Criteria isNull(String property) {
        hibernateQuery.isNull(property)
        return this
    }

    @Override
    Criteria isNotNull(String property) {
        hibernateQuery.isNotNull(property)
        return this
    }

    @Override
    Criteria and(Closure<?> callable) {
        hibernateQuery.and(callable)
        return this
    }

    @Override
    Criteria or(Closure<?> callable) {
        hibernateQuery.or(callable)
        return this
    }

    @Override
    Criteria not(Closure<?> callable) {
        hibernateQuery.not(callable)
        return this
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value. Case-sensitive.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @Override
    Criteria eq(String propertyName, Object propertyValue) {
        return eq(propertyName, propertyValue, Collections.emptyMap())
    }

    @Override
    Criteria idEq(Object o) {
        return eq('id', o)
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value. Supports
     * case-insensitive search if the <code>params</code> map contains <code>true</code> under the
     * 'ignoreCase' key.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @param params optional map with customization parameters; currently only 'ignoreCase' is
     *     supported.
     * @return A Criterion instance
     */
    Criteria eq(String propertyName, Object propertyValue, Map<?, ?> params) {
        if (Boolean.TRUE.equals(params.get('ignoreCase'))) {
            hibernateQuery.like(propertyName, '%' + propertyValue.toString() + '%')
        }
        else {
            hibernateQuery.eq(propertyName, propertyValue)
        }
        return this
    }

    @SuppressWarnings('rawtypes')
    Criteria eq(Map params, String propertyName, Object propertyValue) {
        return eq(propertyName, propertyValue, params)
    }

    /**
     * Creates a Criterion with from the specified property name and "like" expression
     *
     * @param propertyName The property name
     * @param propertyValue The like value
     * @return A Criterion instance
     */
    @Override
    Criteria like(String propertyName, Object propertyValue) {
        hibernateQuery.like(propertyName, propertyValue.toString())
        return this
    }

    /**
     * Creates a Criterion with from the specified property name and "ilike" (a case sensitive version
     * of "like") expression
     *
     * @param propertyName The property name
     * @param propertyValue The ilike value
     * @return A Criterion instance
     */
    @Override
    Criteria ilike(String propertyName, Object propertyValue) {
        hibernateQuery.ilike(propertyName, propertyValue.toString())
        return this
    }

    /**
     * Applys a "in" contrain on the specified property
     *
     * @param propertyName The property name
     * @param values A collection of values
     * @return A Criterion instance
     */
    @Override
    @SuppressWarnings('rawtypes')
    Criteria in(String propertyName, Collection values) {
        hibernateQuery.in(propertyName, values.stream().toList())
        return this
    }

    /** Delegates to in as in is a Groovy keyword */
    @Override
    @SuppressWarnings('rawtypes')
    Criteria inList(String propertyName, Collection values) {
        return in(propertyName, values)
    }

    /** Delegates to in as in is a Groovy keyword */
    @Override
    Criteria inList(String propertyName, Object... values) {
        return in(propertyName, values)
    }

    /**
     * Applys a "in" contrain on the specified property
     *
     * @param propertyName The property name
     * @param values A collection of values
     * @return A Criterion instance
     */
    @Override
    Criteria in(String propertyName, Object... values) {
        hibernateQuery.in(propertyName, List.of(values))
        return this
    }

    /**
     * Orders by the specified property name (defaults to ascending)
     *
     * @param propertyName The property name to order by
     * @return A Order instance
     */
    @Override
    Criteria order(String propertyName) {
        order(new Query.Order(propertyName))
        return this
    }

    @Override
    Criteria order(Query.Order o) {
        hibernateQuery.order(o)
        return this
    }

    Criteria firstResult(int offset) {
        hibernateQuery.firstResult(offset)
        return this
    }

    /**
     * Orders by the specified property name and direction
     *
     * @param propertyName The property name to order by
     * @param directionString Either "asc" for ascending or "desc" for descending
     * @return A Order instance
     */
    @Override
    Criteria order(String propertyName, String directionString) {
        Query.Order.Direction direction = Query.Order.Direction.DESC.name().equalsIgnoreCase(directionString) ?
                Query.Order.Direction.DESC :
                Query.Order.Direction.ASC
        hibernateQuery.order(new Query.Order(propertyName, direction))
        return this
    }

    /**
     * Creates a Criterion that contrains a collection property by size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     * @return A Criterion instance
     */
    @Override
    Criteria sizeEq(String propertyName, int size) {
        hibernateQuery.sizeEq(propertyName, size)
        return this
    }

    /**
     * Creates a Criterion that contrains a collection property to be greater than the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     * @return A Criterion instance
     */
    @Override
    Criteria sizeGt(String propertyName, int size) {
        hibernateQuery.sizeGt(propertyName, size)
        return this
    }

    /**
     * Creates a Criterion that contrains a collection property to be greater than or equal to the
     * given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     * @return A Criterion instance
     */
    @Override
    Criteria sizeGe(String propertyName, int size) {
        hibernateQuery.sizeGe(propertyName, size)
        return this
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than or equal to the given
     * size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     * @return A Criterion instance
     */
    @Override
    Criteria sizeLe(String propertyName, int size) {
        hibernateQuery.sizeLe(propertyName, size)
        return this
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     * @return A Criterion instance
     */
    @Override
    Criteria sizeLt(String propertyName, int size) {
        hibernateQuery.sizeLt(propertyName, size)
        return this
    }

    /**
     * Creates a Criterion with from the specified property name and "rlike" (a regular expression
     * version of "like") expression
     *
     * @param propertyName The property name
     * @param propertyValue The ilike value
     * @return A Criterion instance
     */
    @Override
    org.grails.datastore.mapping.query.api.Criteria rlike(String propertyName, Object propertyValue) {
        hibernateQuery.rlike(propertyName, propertyValue.toString())
        return this
    }

    /**
     * Creates a Criterion that contrains a collection property to be not equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     * @return A Criterion instance
     */
    @Override
    Criteria sizeNe(String propertyName, int size) {
        hibernateQuery.sizeNe(propertyName, size)
        return this
    }

    /**
     * Creates a "not equal" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return The criterion object
     */
    @Override
    Criteria ne(String propertyName, Object propertyValue) {
        hibernateQuery.ne(propertyName, propertyValue)
        return this
    }

    /**
     * Creates a "between" Criterion based on the property name and specified lo and hi values
     *
     * @param propertyName The property name
     * @param lo The low value
     * @param hi The high value
     * @return A Criterion instance
     */
    @Override
    Criteria between(String propertyName, Object lo, Object hi) {
        hibernateQuery.between(propertyName, lo, hi)
        return this
    }

    @Override
    Criteria gte(String s, Object o) {
        return ge(s, o)
    }

    @Override
    Object list(@DelegatesTo(Criteria) Closure<?> c) {
        hibernateQuery.setDetachedCriteria(new DetachedCriteria<>(targetClass))
        return invokeMethod(CriteriaMethods.LIST_CALL.getName(), [c] as Object[])
    }

    List<?> list() {
        return hibernateQuery.list()
    }

    Object singleResult() {
        return hibernateQuery.singleResult()
    }

    @Override
    Object list(Map<String, ?> params, @DelegatesTo(Criteria) Closure<?> c) {
        hibernateQuery.setDetachedCriteria(new DetachedCriteria<>(targetClass))
        return invokeMethod(CriteriaMethods.LIST_CALL.getName(), [params, c] as Object[])
    }

    @Override
    Object listDistinct(@DelegatesTo(Criteria) Closure<?> c) {
        return invokeMethod(CriteriaMethods.LIST_DISTINCT_CALL.getName(), [c] as Object[])
    }

    @Override
    Object get(@DelegatesTo(Criteria) Closure<?> c) {
        return invokeMethod(CriteriaMethods.GET_CALL.getName(), [c] as Object[])
    }

    @Override
    Object scroll(@DelegatesTo(Criteria) Closure<?> c) {
        return invokeMethod(CriteriaMethods.SCROLL_CALL.getName(), [c] as Object[])
    }

    JoinType convertFromInt(Integer from) {
        switch (from) {
            case 1:
                return JoinType.LEFT
            case 2:
                return JoinType.RIGHT
            default:
                return JoinType.INNER
        }
    }

    @SuppressWarnings('rawtypes')
    @Override
    Object invokeMethod(String name, Object obj) {
        Object[] args = obj.getClass().isArray() ?
                (Object[]) obj :
                (obj instanceof Collection ? ((Collection) obj).toArray() : [obj] as Object[])
        return criteriaMethodInvoker.invokeMethod(name, args)
    }

    /**
     * Set this to customize the methods being supported
     * @param criteriaMethodInvoker
     */
    void setCriteriaMethodInvoker(CriteriaMethodInvoker criteriaMethodInvoker) {
        this.criteriaMethodInvoker = criteriaMethodInvoker
    }

    /**
     * Returns the criteria instance
     *
     * @return The criteria instance
     */
    CriteriaQuery<?> getInstance() {
        return criteriaQuery
    }

    /** Set whether a unique result should be returned */
    boolean isUniqueResult() {
        return uniqueResult
    }

    void setUniqueResult(boolean uniqueResult) {
        this.uniqueResult = uniqueResult
    }

    boolean isDistinct() {
        return distinct
    }

    void setDistinct(boolean distinct) {
        this.distinct = distinct
    }

    boolean isCount() {
        return count
    }

    void setCount(boolean count) {
        this.count = count
    }

    boolean isPaginationEnabledList() {
        return paginationEnabledList
    }

    void setPaginationEnabledList(boolean paginationEnabledList) {
        this.paginationEnabledList = paginationEnabledList
    }

    boolean isScroll() {
        return scroll
    }

    void setScroll(boolean scroll) {
        this.scroll = scroll
    }

    HibernateQuery getHibernateQuery() {
        return hibernateQuery
    }

    boolean isParticipate() {
        return participate
    }

    SessionFactory getSessionFactory() {
        return sessionFactory
    }

    org.hibernate.query.criteria.HibernateCriteriaBuilder getCriteriaBuilder() {
        return cb
    }

    Class<?> getClassForAssociationType(Attribute<?, ?> type) {
        if (type instanceof PluralAttribute) {
            return ((PluralAttribute<?, ?, ?>) type).getElementType().getJavaType()
        }
        return type.getJavaType()
    }

    /** Throws a runtime exception where necessary to ensure the session gets closed */
    void throwRuntimeException(RuntimeException t) {
        closeSessionFollowingException()
        throw t
    }

    @SuppressWarnings('PMD.NullAssignment')
    private void closeSessionFollowingException() {
        closeSession()
        criteriaQuery = null
    }

    /** Closes the session if it is copen */
    void closeSession() {
        if (!participate) {
            SessionHolder sessionHolder =
                    (SessionHolder) TransactionSynchronizationManager.unbindResource(sessionFactory)
            if (sessionHolder.getSession().isOpen()) {
                sessionHolder.getSession().close()
            }
        }
        hibernateQuery.getSession().disconnect()
    }

    int getDefaultFlushMode() {
        return defaultFlushMode
    }

    final void setDefaultFlushMode(int defaultFlushMode) {
        this.defaultFlushMode = defaultFlushMode
    }

}
