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
package org.grails.datastore.mapping.query

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import jakarta.persistence.FetchType
import jakarta.persistence.FlushModeType
import jakarta.persistence.LockModeType
import jakarta.persistence.criteria.JoinType
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.util.Assert
import org.springframework.util.StringUtils

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.query.api.AssociationCriteria
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent

/**
 * Models a query that can be executed against a data store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings(['rawtypes', 'unchecked'])
abstract class Query implements Cloneable, Serializable {

    protected final transient PersistentEntity entity
    protected final transient Session session
    protected transient Log logger = LogFactory.getLog(this.getClass())

    protected Junction criteria = new Conjunction()
    protected ProjectionList projections = new ProjectionList()
    protected Integer max
    protected Integer offset
    protected List<Order> orderBy = new ArrayList<>()
    protected boolean uniqueResult
    protected Map<String, FetchType> fetchStrategies = new HashMap<>()
    protected Map<String, JoinType> joinTypes = new HashMap<>()
    protected Boolean queryCache
    protected LockModeType lockResult

    protected Query(Session session, PersistentEntity entity) {
        this.entity = entity
        this.session = session
    }

    @Override
    Object clone() {
        Session currentSession = getSession()
        if (currentSession == null) {
            throw new IllegalStateException('Cannot clone a stateless query')
        }
        Query newQuery = currentSession.createQuery(entity.getJavaClass())
        for (Criterion criterion in criteria.getCriteria()) {
            newQuery.add(criterion)
        }
        return newQuery
    }

    /**
     * @return The maximum number of results to return
     */
    Integer getMax() {
        return max
    }

    /**
     * @return The offset of the first result
     */
    Integer getOffset() {
        return offset
    }

    /**
     * @return The criteria defined by this query
     */
    Junction getCriteria() {
        return criteria
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Query join(String property) {
        fetchStrategies.put(property, FetchType.EAGER)
        return this
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Query join(String property, JoinType joinType) {
        fetchStrategies.put(property, FetchType.EAGER)
        joinTypes.put(property, joinType)
        return this
    }

    /**
     * Specifies whether a select (lazy) query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Query select(String property) {
        fetchStrategies.put(property, FetchType.LAZY)
        return this
    }

    /**
     * Specifies whether the query results should be cached (if supported by the underlying datastore)
     *
     * @param cache True if caching should be enabled
     * @return The query
     */
    Query cache(boolean cache) {
        queryCache = cache
        return this
    }

    /**
     * Specifies whether the query should obtain a pessimistic lock
     *
     * @param lock True if a lock should be obtained
     * @return The query
     */
    Query lock(boolean lock) {
        lockResult = LockModeType.PESSIMISTIC_WRITE
        return this
    }

    /**
     * Specifies whether the query should obtain a pessimistic lock
     *
     * @param lock True if a lock should be obtained
     * @return The query
     */
    Query lock(LockModeType lock) {
        lockResult = lock
        return this
    }

    ProjectionList projections() {
        return projections
    }

    /**
     * Adds the specified criterion instance to the query
     *
     * @param criterion The criterion instance
     */
    void add(Criterion criterion) {
        Junction currentJunction = criteria
        add(currentJunction, criterion)
    }

    /**
     * Adds the specified criterion instance to the given junction
     *
     * @param currentJunction The junction to add the criterion to
     * @param criterion The criterion instance
     */
    void add(Junction currentJunction, Criterion criterion) {
        addToJunction(currentJunction, criterion)
    }

    /**
     * @return The session that created the query
     */
    Session getSession() {
        return session
    }

    /**
     * @return The PersistentEntity being query
     */
    PersistentEntity getEntity() {
        return entity
    }

    /**
     * Creates a disjunction (OR) query
     * @return The Junction instance
     */
    Junction disjunction() {
        Junction currentJunction = criteria
        return disjunction(currentJunction)
    }

    /**
     * Creates a conjunction (AND) query
     * @return The Junction instance
     */
    Junction conjunction() {
        Junction currentJunction = criteria
        return conjunction(currentJunction)
    }

    /**
     * Creates a negation of several criterion
     * @return The negation
     */
    Junction negation() {
        Junction currentJunction = criteria
        return negation(currentJunction)
    }

    private Junction negation(Junction currentJunction) {
        Negation dis = new Negation()
        currentJunction.add(dis)
        return dis
    }

    /**
     * Defines the maximum number of results to return
     * @param max The max results
     * @return This query instance
     */
    Query max(int max) {
        this.max = max
        return this
    }

    /**
     * Defines the maximum number of results to return
     * @param max The max results
     * @return This query instance
     */
    Query maxResults(int max) {
        return this.max(max)
    }

    /**
     * Defines the offset (the first result index) of the query
     * @param offset The offset
     * @return This query instance
     */
    Query offset(int offset) {
        this.offset = offset
        return this
    }

    /**
     * Defines the offset (the first result index) of the query
     * @param offset The offset
     * @return This query instance
     */
    Query firstResult(int offset) {
        return this.offset(offset)
    }

    /**
     * Specifies the order of results
     * @param order The order object
     * @return The Query instance
     */
    Query order(Order order) {
        if (order != null) {
            orderBy.add(order)
        }
        return this
    }

    /**
     * Clears all order entries from this query.
     * Subclasses that store orders in additional structures (e.g. JPA criteria) should override this.
     */
    Query clearOrders() {
        orderBy.clear()
        return this
    }

    /**
     * Gets the Order entries for this query
     * @return The order entries
     */
    List<Order> getOrderBy() {
        return orderBy
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query eq(String property, Object value) {
        Object resolved = resolvePropertyValue(entity, property, value)
        if (resolved.is(value)) {
            criteria.add(Restrictions.eq(property, value))
        }
        else {
            criteria.add(Restrictions.eq(property, resolved))
        }
        return this
    }

    @PackageScope
    Object resolvePropertyValue(PersistentEntity entity, String property, Object value) {
        PersistentProperty persistentProperty = entity.getPropertyByName(property)
        Object resolved
        if (persistentProperty instanceof Embedded) {
            resolved = value
        }
        else {
            resolved = resolveIdIfEntity(value)
        }
        return resolved
    }

    /**
     * Shortcut to restrict the query to multiple given property values
     *
     * @param values The values
     * @return This query instance
     */
    Query allEq(Map<String, Object> values) {
        Junction conjunction = conjunction()
        for (String property in values.keySet()) {
            Object value = values.get(property)
            Object resolved = resolvePropertyValue(entity, property, value)
            conjunction.add(Restrictions.eq(property, resolved))
        }
        return this
    }

    /**
     * Used to restrict a value to be empty (such as a blank string or an empty collection)
     *
     * @param property The property name
    */
    Query isEmpty(String property) {
        criteria.add(Restrictions.isEmpty(property))
        return this
    }

    /**
     * Used to restrict a value to be not empty (such as a blank string or an empty collection)
     *
     * @param property The property name
    */
    Query isNotEmpty(String property) {
        criteria.add(Restrictions.isNotEmpty(property))
        return this
    }

    /**
     * Used to restrict a property to be null
     *
     * @param property The property name
    */
    Query isNull(String property) {
        criteria.add(Restrictions.isNull(property))
        return this
    }

    /**
     * Used to restrict a property to be not null
     *
     * @param property The property name
    */
    Query isNotNull(String property) {
        criteria.add(Restrictions.isNotNull(property))
        return this
    }

    /**
     * Creates an association query
     *
     * @param associationName The association name
     * @return The Query instance
     */
    AssociationQuery createQuery(String associationName) {
        final PersistentProperty property = entity.getPropertyByName(associationName)
        if (property == null || !(property instanceof Association)) {
            throw new InvalidDataAccessResourceUsageException('Cannot query association [' +
                  associationName + '] of class [' + entity +
                  ']. The specified property is not an association.')
        }

        Association association = (Association) property

        final PersistentEntity associatedEntity = association.getAssociatedEntity()

        return new AssociationQuery(session, associatedEntity, association)
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param value The value to restrict by
     * @return This query instance
     */
    Query idEq(Object value) {
        Object resolved = resolveIdIfEntity(value)

        criteria.add(Restrictions.idEq(resolved))
        return this
    }

    /**
     * Used to restrict a value to be greater than the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query gt(String property, Object value) {
        criteria.add(Restrictions.gt(property, value))
        return this
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query gte(String property, Object value) {
        criteria.add(Restrictions.gte(property, value))
        return this
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query lte(String property, Object value) {
        criteria.add(Restrictions.lte(property, value))
        return this
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query ge(String property, Object value) {
        return gte(property, value)
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query le(String property, Object value) {
        return lte(property, value)
    }

    /**
     * Used to restrict a value to be less than the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    Query lt(String property, Object value) {
        criteria.add(Restrictions.lt(property, value))
        return this
    }

    /**
     * Restricts the results by the given property values
     *
     * @param property The name of the property
     * @param values The values to restrict by
     * @return This query instance
     */
    Query in(String property, List values) {
        criteria.add(Restrictions.in(property, values))
        return this
    }

    /**
     * Restricts the results by the given property value range
     *
     * @param property The name of the property
     * @param start The start of the range
     * @param end The end of the range
     * @return This query instance
     */
    Query between(String property, Object start, Object end) {
        criteria.add(Restrictions.between(property, start, end))
        return this
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param property The name of the property
     * @param expr The expression to restrict by
     * @return This query instance
     */
    Query like(String property, String expr) {
        criteria.add(Restrictions.like(property, expr))
        return this
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param property The name of the property
     * @param expr The expression to restrict by
     * @return This query instance
     */
    Query ilike(String property, String expr) {
        criteria.add(Restrictions.ilike(property, expr))
        return this
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param property The name of the property
     * @param expr The expression to restrict by
     * @return This query instance
     */
    Query rlike(String property, String expr) {
        criteria.add(Restrictions.rlike(property, expr))
        return this
    }

    /**
     * Creates a conjunction using two specified criterion
     *
     * @param a The left hand side
     * @param b The right hand side
     * @return This query instance
     */
    Query and(Criterion a, Criterion b) {
        Assert.notNull(a, 'Left hand side of AND cannot be null')
        Assert.notNull(b, 'Right hand side of AND cannot be null')
        criteria.add(Restrictions.and(a, b))
        return this
    }

    /**
     * Creates a disjunction using two specified criterion
     *
     * @param a The left hand side
     * @param b The right hand side
     * @return This query instance
     */
    Query or(Criterion a, Criterion b) {
        Assert.notNull(a, 'Left hand side of AND cannot be null')
        Assert.notNull(b, 'Right hand side of AND cannot be null')
        criteria.add(Restrictions.or(a, b))
        return this
    }

    /**
     * Executes the query returning zero or many results as a list.
     *
     * @return The results
     */
    List list() {
        uniqueResult = false
        return doList()
    }

    /**
     * Executes the query returning a single result or null
     * @return The result
     */
    Object singleResult() {
        uniqueResult = true
        List results = doList()
        return results.isEmpty() ? null : results.get(0)
    }

    /**
     * Counts the rows this query would return, respecting any existing projections or grouping.
     * Subclasses may override to provide an optimized implementation (e.g., derived-table count).
     * The default implementation falls back to loading all rows when user-defined projections
     * exist, since appending a count projection would produce incorrect results.
     *
     * @return The row count
     * @since 8.0
     */
    Number countResults() {
        if (!projections.getProjectionList().isEmpty()) {
            // When user-defined projections exist (e.g. groupProperty + count),
            // a simple count() projection returns incorrect results because it
            // appends to the existing projections rather than replacing them.
            // Fall back to counting the grouped result rows.
            // Hibernate 7 resolves this via HibernateQuery.countResults() using derived-table subqueries.
            logger.warn('DetachedCriteria.count() with user-defined projections cannot use a SQL count query ' +
                'due to a datastore limitation. All grouped result rows will be loaded into memory to ' +
                'determine the count. This may impact performance on large result sets.')
            return list().size()
        }
        projections().count()
        return (Number) singleResult()
    }

    private List doList() {
        flushBeforeQuery()

        ApplicationEventPublisher publisher = session.getDatastore().getApplicationEventPublisher()
        if (publisher != null) {
            publisher.publishEvent(new PreQueryEvent(this))
        }

        List results = executeQuery(entity, criteria)

        if (publisher != null) {
            PostQueryEvent postQueryEvent = new PostQueryEvent(this, results)
            publisher.publishEvent(postQueryEvent)
            results = postQueryEvent.getResults()
        }

        return results
    }

    /**
     * Here purely for compatibility
     *
     * @param uniqueResult Whether it is a unique result
     * @deprecated
     */
    @Deprecated
    void setUniqueResult(boolean uniqueResult) {
        this.uniqueResult = uniqueResult
    }

    /**
     * Obtain the fetch strategy for the given property
     *
     * @param property The fetch strategy
     * @return A specific strategy or lazy by default
     */
    protected FetchType fetchStrategy(String property) {
        final FetchType fetchType = fetchStrategies.get(property)
        if (fetchType != null) {
            return fetchType
        }
        else {
            final PersistentProperty prop = entity.getPropertyByName(property)
            if (prop != null) {
                return prop.getMapping().getMappedForm().getFetchStrategy()
            }
        }
        return FetchType.LAZY
    }

    /**
     * Subclasses should implement this to provide the concrete implementation
     * of querying
     *
     * @param entity The entity
     * @param criteria The criteria
     * @return The results
     */
    protected abstract List executeQuery(PersistentEntity entity, Junction criteria)

    protected Object resolveIdIfEntity(Object value) {
        // use the object id as the value if its a persistent entity
        MappingContext mappingContext = entity.getMappingContext()
        if (mappingContext.getProxyFactory().isProxy(value)) {
            return mappingContext.getProxyFactory().getIdentifier(value)
        }
        return mappingContext.isPersistentEntity(value) ? findInstanceId(value) : value
    }

    private Serializable findInstanceId(Object value) {
        MappingContext ctx = entity.getMappingContext()
        PersistentEntity pe = ctx.getPersistentEntity(value.getClass().getName())
        return ctx.getEntityReflector(pe).getIdentifier(value)
    }

    private Junction disjunction(Junction currentJunction) {
        Disjunction dis = new Disjunction()
        currentJunction.add(dis)
        return dis
    }

    private Junction conjunction(Junction currentJunction) {
        Conjunction con = new Conjunction()
        currentJunction.add(con)
        return con
    }

    /**
     * Converts a pattern to regex for regex queruies
     *
     * @param value The value
     * @return The pattern
     */
    static String patternToRegex(Object value) {
        Object pattern = value == null ? 'null' : value

        String[] array = pattern.toString().split('%', -1)
        for (int i = 0; i < array.length; i++) {
            array[i] = Pattern.quote(array[i])
        }
        String expr = StringUtils.arrayToDelimitedString(array, '.*')
        if (!expr.startsWith('.*')) {
            expr = '^' + expr
        }
        if (!expr.endsWith('.*')) {
            expr = expr + '$'
        }
        return expr
    }

    /**
     * Default behavior is the flush the session before a query in the case of FlushModeType.AUTO.
     * Subclasses can override this method to disable that.
     */
    protected void flushBeforeQuery() {
        // flush before query execution in FlushModeType.AUTO
        if (session != null && session.getFlushMode() == FlushModeType.AUTO) {
            session.flush()
        }
    }

    /**
     * A criterion is used to restrict the results of a query
     */
    private void addToJunction(Junction currentJunction, Criterion criterion) {
        if (criterion instanceof PropertyCriterion) {
            final PropertyCriterion pc = (PropertyCriterion) criterion
            String property = pc.getProperty()
            Object value = resolvePropertyValue(entity, property, pc.getValue())
            pc.setValue(value)
        }
        if (criterion instanceof AssociationCriteria) {
            AssociationCriteria ac = (AssociationCriteria) criterion
            AssociationQuery associationQuery = createQuery(ac.getAssociation().getName())
            for (Criterion associationCriterion in ac.getCriteria()) {
                associationQuery.add(associationCriterion)
            }
            currentJunction.add(associationQuery)
        }
        else if (criterion instanceof Junction) {
            Junction j = (Junction) criterion
            Junction newj
            if (j instanceof Disjunction) {
                newj = disjunction(currentJunction)
            }
            else if (j instanceof Negation) {
                newj = negation(currentJunction)
            }
            else {
                newj = conjunction(currentJunction)
            }
            for (Criterion c in j.getCriteria()) {
                addToJunction(newj, c)
            }
        }
        else {
            currentJunction.add(criterion)
        }
    }

    /**
     * Represents a criterion to be used in a criteria query
     */
    /**
     * Common interface for all query elements
     */
    static interface QueryElement extends Serializable { }

    static interface Criterion extends QueryElement { }

    /**
     * The ordering of results.
     */
    static class Order implements Serializable {

        private static final long serialVersionUID = 1L
        private Direction direction = Direction.ASC
        private String property
        private boolean ignoreCase = false

        Order(String property) {
            this.property = property
        }

        Order(String property, Direction direction) {
            this.direction = direction
            this.property = property
        }

        /**
         * Whether to ignore the case for this order definition
         *
         * @return This order instance
         */
        Order ignoreCase() {
            this.ignoreCase = true
            return this
        }

        boolean isIgnoreCase() {
            return ignoreCase
        }

        /**
         * @return The direction order by
         */
        Direction getDirection() {
            return direction
        }

        /**
         * @return The property name to order by
         */
        String getProperty() {
            return property
        }

        /**
         * Creates a new order for the given property in descending order
         *
         * @param property The property
         * @return The order instance
         */
        static Order desc(String property) {
            return new Order(property, Direction.DESC)
        }

        /**
         * Creates a new order for the given property in ascending order
         *
         * @param property The property
         * @return The order instance
         */
        static Order asc(String property) {
            return new Order(property, Direction.ASC)
        }

        /**
         * Represents the direction of the ordering
         */
        static enum Direction {
            ASC, DESC
        }

    }

    /**
     * Restricts a property to be null
     */
    static class IsNull extends PropertyNameCriterion {

        IsNull(String name) {
            super(name)
        }

    }

    /**
     * Restricts a property to be empty (such as a blank string)
     */
    static class IsEmpty extends PropertyNameCriterion {

        IsEmpty(String name) {
            super(name)
        }

    }

    /**
     * Restricts a property to be empty (such as a blank string)
     */
    static class IsNotEmpty extends PropertyNameCriterion {

        IsNotEmpty(String name) {
            super(name)
        }

    }

    /**
     * Restricts a property to be not null
     */
    static class IsNotNull extends PropertyNameCriterion {

        IsNotNull(String name) {
            super(name)
        }

    }

    /**
     * A Criterion that applies to a property
     */
    static class PropertyNameCriterion implements Criterion {

        protected String name

        PropertyNameCriterion(String name) {
            this.name = name
        }

        String getProperty() {
            return name
        }

    }

    /**
     * A Criterion that compares to properties
     */
    static class PropertyComparisonCriterion extends PropertyNameCriterion {

        protected String otherProperty

        PropertyComparisonCriterion(String property, String otherProperty) {
            super(property)
            this.otherProperty = otherProperty
        }

        String getOtherProperty() {
            return otherProperty
        }

    }

    static class EqualsProperty extends PropertyComparisonCriterion {

        EqualsProperty(String property, String otherProperty) {
            super(property, otherProperty)
        }

    }

    static class NotEqualsProperty extends PropertyComparisonCriterion {

        NotEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty)
        }

    }

    static class GreaterThanProperty extends PropertyComparisonCriterion {

        GreaterThanProperty(String property, String otherProperty) {
            super(property, otherProperty)
        }

    }

    static class GreaterThanEqualsProperty extends PropertyComparisonCriterion {

        GreaterThanEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty)
        }

    }

    static class LessThanProperty extends PropertyComparisonCriterion {

        LessThanProperty(String property, String otherProperty) {
            super(property, otherProperty)
        }

    }

    static class LessThanEqualsProperty extends PropertyComparisonCriterion {

        LessThanEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty)
        }

    }

    /**
     * Criterion that applies to a property and value
     */
    static class PropertyCriterion extends PropertyNameCriterion {

        protected Object value

        PropertyCriterion(String name, Object value) {
            super(name)
            this.value = value
        }

        Object getValue() {
            return value
        }

        void setValue(Object v) {
            this.value = v
        }

    }

    /**
     * Used to differentiate criterion that require a subquery
     */
    static class SubqueryCriterion extends PropertyCriterion {

        SubqueryCriterion(String name, QueryableCriteria value) {
            super(name, value)
        }

        @Override
        QueryableCriteria getValue() {
            return (QueryableCriteria) super.getValue()
        }

    }

    /**
     * Restricts a value to be equal to all the given values
     */
    static class EqualsAll extends SubqueryCriterion {

        EqualsAll(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be not equal to all the given values
     */
    static class NotEqualsAll extends SubqueryCriterion {

        NotEqualsAll(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be greater than all the given values
     */
    static class GreaterThanAll extends SubqueryCriterion {

        GreaterThanAll(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be greater than some of the given values
     */
    static class GreaterThanSome extends SubqueryCriterion {

        GreaterThanSome(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be greater than some of the given values
     */
    static class GreaterThanEqualsSome extends SubqueryCriterion {

        GreaterThanEqualsSome(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be less than some of the given values
     */
    static class LessThanSome extends SubqueryCriterion {

        LessThanSome(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be less than some of the given values
     */
    static class LessThanEqualsSome extends SubqueryCriterion {

        LessThanEqualsSome(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be less than all the given values
     */
    static class LessThanAll extends SubqueryCriterion {

        LessThanAll(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be greater than or equal to all the given values
     */
    static class GreaterThanEqualsAll extends SubqueryCriterion {

        GreaterThanEqualsAll(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * Restricts a value to be less than or equal to all the given values
     */
    static class LessThanEqualsAll extends SubqueryCriterion {

        LessThanEqualsAll(String name, QueryableCriteria value) {
            super(name, value)
        }

    }

    /**
     * A criterion that restricts the results based on equality
     */
    static class Equals extends PropertyCriterion {

        Equals(String name, Object value) {
            super(name, value)
        }

        @Override
        void setValue(Object value) {
            this.@value = value
        }

    }

    static class SizeEquals extends PropertyCriterion {

        SizeEquals(String name, int value) {
            super(name, value)
        }

    }

    static class SizeNotEquals extends PropertyCriterion {

        SizeNotEquals(String name, int value) {
            super(name, value)
        }

    }

    static class SizeGreaterThan extends PropertyCriterion {

        SizeGreaterThan(String name, int value) {
            super(name, value)
        }

    }

    static class SizeGreaterThanEquals extends PropertyCriterion {

        SizeGreaterThanEquals(String name, int value) {
            super(name, value)
        }

    }

    static class SizeLessThanEquals extends PropertyCriterion {

        SizeLessThanEquals(String name, int value) {
            super(name, value)
        }

    }

    static class SizeLessThan extends PropertyCriterion {

        SizeLessThan(String name, int value) {
            super(name, value)
        }

    }

    /**
     * A criterion that restricts the results based on the equality of the identifier
     */
    static class IdEquals extends PropertyCriterion {

        private static final String ID = 'id'

        IdEquals(Object value) {
            super(ID, value)
        }

        @Override
        void setValue(Object value) {
            this.@value = value
        }

    }

    /**
     * A criterion that restricts the results based on equality
     */
    static class NotEquals extends PropertyCriterion {

        NotEquals(String name, Object value) {
            super(name, value)
        }

        @Override
        void setValue(Object value) {
            this.@value = value
        }

    }

    /**
     * Criterion used to restrict the results based on a list of values
     */
    static class In extends PropertyCriterion {

        private Collection values = Collections.emptyList()
        private QueryableCriteria subquery

        In(String name, Collection values) {
            super(name, values)
            this.values = convertCharSequenceValuesIfNecessary(values)
        }

        private static Collection convertCharSequenceValuesIfNecessary(Collection values) {
            boolean requiresConversion = false
            for (Object val in values) {
                if (!(val instanceof String) && val instanceof CharSequence) {
                    requiresConversion = true
                    break
                }
            }
            if (requiresConversion) {
                List newList = new ArrayList(values.size())
                for (Object val in values) {
                    newList.add(val instanceof CharSequence ? val.toString() : val)
                }
                return newList
            }
            else {
                return values
            }
        }

        In(String name, QueryableCriteria subquery) {
            super(name, subquery)
            this.subquery = subquery
        }

        String getName() {
            return getProperty()
        }

        Collection getValues() {
            return Collections.unmodifiableCollection(values)
        }

        QueryableCriteria getSubquery() {
            return subquery
        }

    }

    /**
     * Criterion used to restrict the results based on a list of values
     */
    static class NotIn extends SubqueryCriterion {

        private QueryableCriteria subquery

        NotIn(String name, QueryableCriteria subquery) {
            super(name, subquery)
            this.subquery = subquery
        }

        String getName() {
            return getProperty()
        }

        QueryableCriteria getSubquery() {
            return subquery
        }

    }

    /**
     * Used for exists subquery
     */
    static class Exists implements Criterion {

        private QueryableCriteria subquery

        Exists(QueryableCriteria subquery) {
            this.subquery = subquery
        }

        QueryableCriteria getSubquery() {
            return subquery
        }

    }

    /**
     * Used for exists subquery
     */
    static class NotExists implements Criterion {

        private QueryableCriteria subquery

        NotExists(QueryableCriteria subquery) {
            this.subquery = subquery
        }

        QueryableCriteria getSubquery() {
            return subquery
        }

    }

    /**
     * Used to restrict a value to be greater than the given value
     */
    static class GreaterThan extends PropertyCriterion {

        GreaterThan(String name, Object value) {
            super(name, value)
        }

    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     */
    static class GreaterThanEquals extends PropertyCriterion {

        GreaterThanEquals(String name, Object value) {
            super(name, value)
        }

    }

    /**
     * Used to restrict a value to be less than the given value
     */
    static class LessThan extends PropertyCriterion {

        LessThan(String name, Object value) {
            super(name, value)
        }

    }

    /**
     * Used to restrict a value to be less than the given value
     */
    static class LessThanEquals extends PropertyCriterion {

        LessThanEquals(String name, Object value) {
            super(name, value)
        }

    }

    /**
     * Criterion used to restrict the result to be between values (range query)
     */
    static class Between extends PropertyCriterion {

        private String property
        private Object from
        private Object to

        Between(String property, Object from, Object to) {
            super(property, from)
            this.property = property
            this.from = from
            this.to = to
        }

        @Override
        String getProperty() {
            return property
        }

        Object getFrom() {
            return from
        }

        Object getTo() {
            return to
        }

    }

    /**
     * Criterion used to restrict the results based on a pattern (likeness)
     */
    static class Like extends PropertyCriterion {

        Like(String name, String expression) {
            super(name, expression)
        }

        String getPattern() {
            return getValue().toString()
        }

    }

    /**
     * Criterion used to restrict the results based on a pattern (likeness)
     */
    static class ILike extends Like {

        ILike(String name, String expression) {
            super(name, expression)
        }

    }

    /**
     * Criterion used to restrict the results based on a regular expression pattern
     */
    static class RLike extends Like {

        RLike(String name, String expression) {
            super(name, expression)
        }

        @Override
        String getPattern() {
            return getValue().toString()
        }

    }

    static abstract class Junction implements Criterion {

        private List<Criterion> criteria = new ArrayList<>()

        protected Junction() {
        }

        Junction(List<Criterion> criteria) {
            this.criteria = criteria
        }

        Junction add(Criterion c) {
            if (c != null) {
                criteria.add(c)
            }
            return this
        }

        List<Criterion> getCriteria() {
            return criteria
        }

        boolean isEmpty() {
            return criteria.isEmpty()
        }

    }

    /**
     * A Criterion used to combine to criterion in a logical AND
     */
    static class Conjunction extends Junction {

        Conjunction() {
        }

        Conjunction(List<Criterion> criteria) {
            super(criteria)
        }

    }

    /**
     * A Criterion used to combine to criterion in a logical OR
     */
    static class Disjunction extends Junction {

        Disjunction() {
        }

        Disjunction(List<Criterion> criteria) {
            super(criteria)
        }

    }

    /**
     * A criterion used to negate several other criterion
     */
    static class Negation extends Junction { }

    /**
     * A projection
     */
    static class Projection implements QueryElement { }

    /**
     * A projection used to obtain the identifier of an object
     */
    static class IdProjection extends Projection { }

    /**
     * Used to count the results of a query
     */
    static class CountProjection extends Projection { }

    static class DistinctProjection extends Projection { }

    /**
     * A projection that obtains the value of a property of an entity
     */
    static class PropertyProjection extends Projection {

        private String propertyName

        protected PropertyProjection(String propertyName) {
            this.propertyName = propertyName
        }

        String getPropertyName() {
            return propertyName
        }

    }

    static class DistinctPropertyProjection extends PropertyProjection {

        protected DistinctPropertyProjection(String propertyName) {
            super(propertyName)
        }

    }

    static class CountDistinctProjection extends PropertyProjection {

        CountDistinctProjection(String property) {
            super(property)
        }

    }

    static class GroupPropertyProjection extends PropertyProjection {

        GroupPropertyProjection(String property) {
            super(property)
        }

    }

    /**
     * Computes the average value of a property
     */
    static class AvgProjection extends PropertyProjection {

        protected AvgProjection(String propertyName) {
            super(propertyName)
        }

    }

    /**
     * Computes the max value of a property
     */
    static class MaxProjection extends PropertyProjection {

        protected MaxProjection(String propertyName) {
            super(propertyName)
        }

    }

    /**
     * Computes the min value of a property
     */
    static class MinProjection extends PropertyProjection {

        protected MinProjection(String propertyName) {
            super(propertyName)
        }

    }

    /**
     * Computes the sum of a property
     */
    static class SumProjection extends PropertyProjection {

        protected SumProjection(String propertyName) {
            super(propertyName)
        }

    }

    /**
     * A list of projections
     */
    static class ProjectionList implements org.grails.datastore.mapping.query.api.ProjectionList {

        private List<Projection> projections = new ArrayList()

        List<Projection> getProjectionList() {
            return Collections.unmodifiableList(projections)
        }

        ProjectionList add(Projection p) {
            projections.add(p)
            return this
        }

        org.grails.datastore.mapping.query.api.ProjectionList id() {
            add(Projections.id())
            return this
        }

        org.grails.datastore.mapping.query.api.ProjectionList count() {
            add(Projections.count())
            return this
        }

        org.grails.datastore.mapping.query.api.ProjectionList countDistinct(String property) {
            add(Projections.countDistinct(property))
            return this
        }

        @Override
        org.grails.datastore.mapping.query.api.ProjectionList groupProperty(String property) {
            add(Projections.groupProperty(property))
            return this
        }

        boolean isEmpty() {
            return projections.isEmpty()
        }

        ProjectionList distinct() {
            add(Projections.distinct())
            return this
        }

        org.grails.datastore.mapping.query.api.ProjectionList distinct(String property) {
            add(Projections.distinct(property))
            return this
        }

        org.grails.datastore.mapping.query.api.ProjectionList rowCount() {
            return count()
        }

        /**
         * A projection that obtains the value of a property of an entity
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        org.grails.datastore.mapping.query.api.ProjectionList property(String name) {
            add(Projections.property(name))
            return this
        }

        /**
         * Computes the sum of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        org.grails.datastore.mapping.query.api.ProjectionList sum(String name) {
            add(Projections.sum(name))
            return this
        }

        /**
         * Computes the min value of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        org.grails.datastore.mapping.query.api.ProjectionList min(String name) {
            add(Projections.min(name))
            return this
        }

        /**
         * Computes the max value of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        org.grails.datastore.mapping.query.api.ProjectionList max(String name) {
            add(Projections.max(name))
            return this
        }

        /**
         * Computes the average value of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        org.grails.datastore.mapping.query.api.ProjectionList avg(String name) {
            add(Projections.avg(name))
            return this
        }

    }

}
