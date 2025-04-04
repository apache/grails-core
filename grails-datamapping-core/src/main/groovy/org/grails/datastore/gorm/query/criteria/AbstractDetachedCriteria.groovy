package org.grails.datastore.gorm.query.criteria

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.mapping.query.api.ProjectionList
import org.grails.datastore.mapping.query.api.QueryableCriteria

import jakarta.persistence.FetchType
import jakarta.persistence.criteria.JoinType

/**
 * Abstract super class for DetachedCriteria implementations
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
abstract class AbstractDetachedCriteria<T> implements Criteria, Cloneable {

    protected List<Query.Criterion> criteria = []
    protected List<Query.Order> orders = []
    protected List<Query.Projection> projections = []
    protected Class targetClass
    protected List<DynamicFinder> dynamicFinders
    protected Integer defaultOffset
    protected Integer defaultMax

    protected List<Query.Junction> junctions = []
    protected PersistentEntity persistentEntity
    protected Map<String, FetchType> fetchStrategies = [:]
    protected Map<String, JoinType> joinTypes = [:]
    protected Closure lazyQuery
    protected String alias;
    protected String connectionName = ConnectionSource.DEFAULT
    protected Map<String, DetachedAssociationCriteria> associationCriteriaMap = [:]


    ProjectionList projectionList = new DetachedProjections(projections)


    /**
     * Constructs a DetachedCriteria instance target the given class and alias for the name
     * @param targetClass The target class
     * @param alias The root alias to be used in queries
     */
    AbstractDetachedCriteria(Class<T> targetClass, String alias = null) {
        this.targetClass = targetClass
        this.alias = alias
    }

    /**
     * @return Obtain the fetch strategies for each property
     */
    Map<String, FetchType> getFetchStrategies() {
        return Collections.unmodifiableMap( fetchStrategies )
    }

    /**
     * @return Obtain the join types
     */
    Map<String, JoinType> getJoinTypes() {
        return Collections.unmodifiableMap( joinTypes )
    }
    /**
     * @return The root alias to be used for the query
     */
    String getAlias() {
        return this.alias
    }

    /**
     * Sets the root alias to be used for the query
     * @param alias The alias
     * @return The alias
     */
    Criteria setAlias(String alias) {
        this.alias = alias
        return this
    }

    /**
     * If the underlying datastore supports aliases, then an alias is created for the given association
     *
     * @param associationPath The name of the association
     * @param alias The alias
     * @return This create
     */
    Criteria createAlias(String associationPath, String alias) {
        initialiseIfNecessary(targetClass)
        PersistentProperty prop
        if(associationPath.contains('.')) {
            def tokens = associationPath.split(/\./)
            def entity = this.persistentEntity
            for(t in tokens) {
                prop = entity.getPropertyByName(t)
                if (!(prop instanceof Association)) {
                    throw new IllegalArgumentException("Argument [$associationPath] is not an association")
                }
                else {
                    entity = ((Association)prop).associatedEntity
                }
            }
        }
        else {
            prop = persistentEntity.getPropertyByName(associationPath)
        }
        if (!(prop instanceof Association)) {
            throw new IllegalArgumentException("Argument [$associationPath] is not an association")
        }

        Association a = (Association)prop
        DetachedAssociationCriteria associationCriteria = associationCriteriaMap[associationPath]
        if(associationCriteria == null) {
            associationCriteria = new DetachedAssociationCriteria(a.associatedEntity.javaClass, a, associationPath, alias)
            associationCriteriaMap[associationPath] = associationCriteria
            add associationCriteria
        }
        else {
            associationCriteria.setAlias(alias)
        }
        return associationCriteria
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Criteria join(String property) {
        fetchStrategies[property] = FetchType.EAGER
        return this
    }


    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @param joinType The join type
     * @return The query
     */
    Criteria join(String property, JoinType joinType) {
        fetchStrategies[property] = FetchType.EAGER
        joinTypes[property] = joinType
        return this
    }

    /**
     * Specifies whether a select (lazy) query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Criteria select(String property) {
        fetchStrategies[property] = FetchType.LAZY
        return this
    }

    T getPersistentClass() {
        (T)getPersistentEntity().getJavaClass()
    }

    PersistentEntity getPersistentEntity() {
        if (persistentEntity == null) initialiseIfNecessary(targetClass)
        return persistentEntity
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected initialiseIfNecessary(Class<T> targetClass) {
        if (dynamicFinders != null) {
            return
        }

        try {
            dynamicFinders = targetClass.gormDynamicFinders
            persistentEntity = targetClass.gormPersistentEntity
        } catch (MissingPropertyException mpe) {
            throw new IllegalArgumentException("Class [$targetClass.name] is not a domain class")
        }
    }

    void add(Query.Criterion criterion) {
        applyLazyCriteria()
        if (criterion instanceof Query.PropertyCriterion) {
            if (criterion.value instanceof Closure) {
                criterion.value = buildQueryableCriteria((Closure)criterion.value)
            }
        }
        if (junctions)  {
            junctions[-1].add criterion
        }
        else {
            criteria << criterion
        }
    }

    List<Query.Criterion> getCriteria() { criteria }

    List<Query.Projection> getProjections() { projections }

    List<Query.Order> getOrders() { orders }

    /**
     * Evaluate projections within the context of the given closure
     *
     * @param callable The callable
     * @return  The projection list
     */
    Criteria projections(@DelegatesTo(ProjectionList) Closure callable) {
        callable.delegate = projectionList
        callable.call()
        return this
    }

    /**
     * Handles a conjunction
     * @param callable Callable closure
     * @return This criterion
     */
    Criteria and(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        junctions << new Query.Conjunction()
        handleJunction(callable)
        return this
    }

    /**
     * Handles a disjunction
     * @param callable Callable closure
     * @return This criterion
     */
    Criteria or(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        junctions << new Query.Disjunction()
        handleJunction(callable)
        return this
    }

    /**
     * Handles a disjunction
     * @param callable Callable closure
     * @return This criterion
     */
    Criteria not(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        junctions << new Query.Negation()
        handleJunction(callable)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria 'in'(String propertyName, Collection values) {
        inList(propertyName, values)
    }

    /**
     * @see Criteria
     */
    Criteria 'in'(String propertyName, QueryableCriteria subquery) {
        inList(propertyName, subquery)
    }

    @Override
    Criteria inList(String propertyName, QueryableCriteria<?> subquery) {
        add Restrictions.in(propertyName, subquery)
        return this
    }

    @Override
    Criteria "in"(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> subquery) {
        inList propertyName, buildQueryableCriteria(subquery)
    }

    @Override
    Criteria inList(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> subquery) {
        inList propertyName, buildQueryableCriteria(subquery)
    }

    /**
     * @see Criteria
     */
    Criteria 'in'(String propertyName, Object[] values) {
        inList(propertyName, values)
    }

    @Override
    Criteria notIn(String propertyName, QueryableCriteria<?> subquery) {
        add Restrictions.notIn(propertyName, subquery)
        return this
    }

    @Override
    Criteria notIn(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> subquery) {
        notIn propertyName, buildQueryableCriteria(subquery)
    }

    /**
     * @see Criteria
     */
    Criteria order(String propertyName) {
        orders << new Query.Order(propertyName)
        return this
    }

    @Override
    Criteria order(Query.Order o) {
        orders << o
        return this
    }

    /**
     * @see Criteria
     */
    Criteria order(String propertyName, String direction) {
        orders << new Query.Order(propertyName, Query.Order.Direction.valueOf(direction.toUpperCase()))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria inList(String propertyName, Collection values) {
        add Restrictions.in(propertyName, convertArgumentList(values))
        return this
    }

    protected List convertArgumentList(Collection argList) {
        List convertedList = new ArrayList(argList.size());
        for (Object item : argList) {
            if(item instanceof CharSequence) {
                item = item.toString();
            }
            convertedList.add(item);
        }
        return convertedList;
    }
    /**
     * @see Criteria
     */
    Criteria inList(String propertyName, Object[] values) {
        add Restrictions.in(propertyName, convertArgumentList(Arrays.asList(values)))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeEq(String propertyName, int size) {
        add Restrictions.sizeEq(propertyName, size)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeGt(String propertyName, int size) {
        add Restrictions.sizeGt(propertyName, size)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeGe(String propertyName, int size) {
        add Restrictions.sizeGe(propertyName, size)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeLe(String propertyName, int size) {
        add Restrictions.sizeLe(propertyName, size)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeLt(String propertyName, int size) {
        add Restrictions.sizeLt(propertyName, size)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeNe(String propertyName, int size) {
        add Restrictions.sizeNe(propertyName, size)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria eqProperty(String propertyName, String otherPropertyName) {
        add Restrictions.eqProperty(propertyName,otherPropertyName)
        return this
    }

    /**
     * @see Criteria#neProperty(java.lang.String, java.lang.String)
     */
    Criteria neProperty(String propertyName, String otherPropertyName) {
        add Restrictions.neProperty(propertyName,otherPropertyName)
        return this
    }

    /**
     * @see Criteria#allEq(java.util.Map)
     */
    @Override
    Criteria allEq(Map<String, Object> propertyValues) {
        Query.Conjunction conjunction = new Query.Conjunction()
        for (property in propertyValues.keySet()) {
            conjunction.add Restrictions.eq(property, propertyValues.get(property))
        }
        add conjunction
        return this
    }

    /**
     * @see Criteria
     */
    Criteria gtProperty(String propertyName, String otherPropertyName) {
        add Restrictions.gtProperty(propertyName,otherPropertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria geProperty(String propertyName, String otherPropertyName) {
        add Restrictions.geProperty(propertyName,otherPropertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ltProperty(String propertyName, String otherPropertyName) {
        add Restrictions.ltProperty(propertyName,otherPropertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria leProperty(String propertyName, String otherPropertyName) {
        add Restrictions.leProperty(propertyName,otherPropertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria idEquals(Object value) {
        add Restrictions.idEq(value)
        return this
    }

    /**
     * @see Criteria#exists(org.grails.datastore.mapping.query.api.QueryableCriteria)
     */
    @Override
    Criteria exists(QueryableCriteria<?> subquery) {
        add new Query.Exists(subquery);
        return this;
    }

    /**
     * @see Criteria#notExists(org.grails.datastore.mapping.query.api.QueryableCriteria)
     */
    @Override
    Criteria notExists(QueryableCriteria<?> subquery) {
        add new Query.NotExists(subquery);
        return this;
    }

    /**
     * @see Criteria
     */
    Criteria isEmpty(String propertyName) {
        add Restrictions.isEmpty(propertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isNotEmpty(String propertyName) {
        add Restrictions.isNotEmpty(propertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isNull(String propertyName) {
        add Restrictions.isNull(propertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isNotNull(String propertyName) {
        add Restrictions.isNotNull(propertyName)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria eq(String propertyName, Object propertyValue) {
        add Restrictions.eq(propertyName,propertyValue)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria idEq(Object propertyValue) {
        add Restrictions.idEq(propertyValue)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ne(String propertyName, Object propertyValue) {
        add Restrictions.ne(propertyName,propertyValue)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria between(String propertyName, Object start, Object finish) {
        add Restrictions.between(propertyName, start, finish)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria gte(String property, Object value) {
        add Restrictions.gte(property,value)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ge(String property, Object value) {
        gte(property, value)
    }

    /**
     * @see Criteria
     */
    Criteria gt(String property, Object value) {
        add Restrictions.gt(property,value)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria lte(String property, Object value) {
        add Restrictions.lte(property, value)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria le(String property, Object value) {
        lte(property,value)
    }

    /**
     * @see Criteria
     */
    Criteria lt(String property, Object value) {
        add Restrictions.lt(property,value)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria like(String propertyName, Object propertyValue) {
        add Restrictions.like(propertyName,propertyValue.toString())
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ilike(String propertyName, Object propertyValue) {
        add Restrictions.ilike(propertyName, propertyValue.toString())
        return this
    }

    /**
     * @see Criteria
     */
    Criteria rlike(String propertyName, Object propertyValue) {
        add Restrictions.rlike(propertyName, propertyValue.toString())
        return this
    }

    Criteria eqAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        eqAll(propertyName, buildQueryableCriteria(propertyValue))
    }

    Criteria gtAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        gtAll(propertyName, buildQueryableCriteria(propertyValue))
    }

    Criteria ltAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        ltAll(propertyName, buildQueryableCriteria(propertyValue))
    }

    Criteria geAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        geAll(propertyName, buildQueryableCriteria(propertyValue))
    }

    Criteria leAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        leAll(propertyName, buildQueryableCriteria(propertyValue))
    }

    @Override
    Criteria eqAll(String propertyName, QueryableCriteria propertyValue) {
        add new Query.EqualsAll(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria gtAll(String propertyName, QueryableCriteria propertyValue) {
        add new Query.GreaterThanAll(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria gtSome(String propertyName, QueryableCriteria propertyValue) {
        add new Query.GreaterThanSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria gtSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        gtSome propertyName, buildQueryableCriteria(propertyValue)
    }

    @Override
    Criteria geSome(String propertyName, QueryableCriteria propertyValue) {
        add new Query.GreaterThanEqualsSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria geSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        geSome propertyName, buildQueryableCriteria(propertyValue)
    }

    @Override
    Criteria ltSome(String propertyName, QueryableCriteria propertyValue) {
        add new Query.LessThanSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria ltSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        ltSome propertyName, buildQueryableCriteria(propertyValue)
    }

    @Override
    Criteria leSome(String propertyName, QueryableCriteria propertyValue) {
        add new Query.LessThanEqualsSome(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria leSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        leSome propertyName, buildQueryableCriteria(propertyValue)
    }

    @Override
    Criteria ltAll(String propertyName, QueryableCriteria propertyValue) {
        add new Query.LessThanAll(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria geAll(String propertyName, QueryableCriteria propertyValue) {
        add new Query.GreaterThanEqualsAll(propertyName, propertyValue)
        return this
    }

    @Override
    Criteria leAll(String propertyName, QueryableCriteria propertyValue) {
        add new Query.LessThanEqualsAll(propertyName, propertyValue)
        return this
    }

    class DetachedProjections implements ProjectionList {

        List<Query.Projection> projections

        DetachedProjections(List<Query.Projection> projections) {
            this.projections = projections
        }

        ProjectionList avg(String name) {
            projections << Projections.avg(name)
            return this
        }

        ProjectionList max(String name) {
            projections << Projections.max(name)
            return this
        }

        ProjectionList min(String name) {
            projections << Projections.min(name)
            return this
        }

        ProjectionList sum(String name) {
            projections << Projections.sum(name)
            return this
        }

        ProjectionList property(String name) {
            projections << Projections.property(name)
            return this
        }

        ProjectionList rowCount() {
            projections << Projections.count()
            return this
        }

        ProjectionList distinct(String property) {
            projections << Projections.distinct(property)
            return this
        }

        ProjectionList distinct() {
            projections << Projections.distinct()
            return this
        }

        ProjectionList countDistinct(String property) {
            projections << Projections.countDistinct(property)
            return this
        }

        @Override
        ProjectionList groupProperty(String property) {
            projections << Projections.groupProperty(property)
            return this
        }

        ProjectionList count() {
            projections << Projections.count()
            return this
        }

        ProjectionList id() {
            projections << Projections.id()
            return this
        }
    }

    /**
     * Where method derives a new query from this query. This method will not mutate the original query, but instead return a new one.
     *
     * @param additionalQuery The additional query
     * @return A new query
     */
    AbstractDetachedCriteria<T> where(@DelegatesTo(AbstractDetachedCriteria) Closure additionalQuery) {
        AbstractDetachedCriteria<T> newQuery = clone()
        return newQuery.build(additionalQuery)
    }

    /**
     * Where method derives a new query from this query. This method will not mutate the original query, but instead return a new one.
     *
     * @param additionalQuery The additional query
     * @return A new query
     */
    AbstractDetachedCriteria<T> whereLazy(@DelegatesTo(AbstractDetachedCriteria) Closure additionalQuery) {
        AbstractDetachedCriteria<T> newQuery = clone()
        return newQuery.build(additionalQuery)
    }


    /**
     * Enable the builder syntax for constructing Criteria
     *
     * @param callable The callable closure
     * @return A new criteria instance
     */

    AbstractDetachedCriteria<T> build(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        AbstractDetachedCriteria newCriteria = this.clone()
        final Closure clonedClosure = (Closure) callable.clone()
        clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
        newCriteria.with(clonedClosure)
        return newCriteria
    }

    /**
     * Enable the builder syntax for constructing Criteria
     *
     * @param callable The callable closure
     * @return A new criteria instance
     */

    AbstractDetachedCriteria<T> buildLazy(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.lazyQuery = callable
        return newCriteria
    }


    /**
     * Create a return a new DetachedCriteria that uses the given connection
     *
     * @param name The connection name
     * @param callable The callable closure
     * @return A new criteria instance
     * @since 6.1
     */

    AbstractDetachedCriteria<T> withConnection(String name) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.connectionName = name
        return newCriteria
    }

    @Override
    @CompileStatic
    protected AbstractDetachedCriteria<T> clone() {
        AbstractDetachedCriteria criteria = newInstance()
        criteria.@criteria = new ArrayList(this.criteria)
        final projections = new ArrayList(this.projections)
        criteria.@projections = projections
        criteria.projectionList = new DetachedProjections(projections)
        criteria.@orders = new ArrayList(this.orders)
        criteria.defaultMax = defaultMax
        criteria.defaultOffset = defaultOffset
        criteria.@fetchStrategies = new HashMap<>(this.fetchStrategies)
        criteria.@joinTypes = new HashMap<>(this.joinTypes)
        return criteria
    }

    protected abstract AbstractDetachedCriteria newInstance()

    /**
     * Sets the default max to use and returns a new criteria instance. This method does not mutate the original criteria!
     *
     * @param max The max to use
     * @return A new DetachedCriteria instance derived from this
     */
    AbstractDetachedCriteria<T> max(int max) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.defaultMax = max
        return newCriteria
    }

    /**
     * Sets the default offset to use and returns a new criteria instance. This method does not mutate the original criteria!
     *
     * @param offset The offset to use
     * @return A new DetachedCriteria instance derived from this
     */
    AbstractDetachedCriteria<T> offset(int offset) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.defaultOffset = offset
        return newCriteria
    }

    /**
     * Adds a sort order to this criteria instance
     *
     * @param property The property to sort by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> sort(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.orders.add(new Query.Order(property))
        return newCriteria
    }

    /**
     * Adds a sort order to this criteria instance
     *
     * @param property The property to sort by
     * @param direction The direction to sort by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> sort(String property, String direction) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.orders.add(new Query.Order(property, "desc".equalsIgnoreCase(direction) ? Query.Order.Direction.DESC : Query.Order.Direction.ASC))
        return newCriteria
    }

    /**
     * Adds a property projection
     *
     * @param property The property to project
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> property(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.property(property)
        return newCriteria
    }

    /**
     * Adds an id projection
     *
     * @param property The property to project
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> id() {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.id()
        return newCriteria
    }

    /**
     * Adds a avg projection
     *
     * @param property The property to avg by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> avg(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.avg(property)
        return newCriteria
    }

    /**
     * Adds a sum projection
     *
     * @param property The property to sum by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> sum(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.sum(property)
        return newCriteria
    }

    /**
     * Adds a sum projection
     *
     * @param property The property to min by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> min(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.min(property)
        return newCriteria
    }

    /**
     * Adds a min projection
     *
     * @param property The property to max by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> max(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.max(property)
        return newCriteria
    }


    def propertyMissing(String name) {
        final entity = getPersistentEntity()
        final p = entity.getPropertyByName(name)
        if (p == null) {
            throw new MissingPropertyException(name, AbstractDetachedCriteria)
        }
        return property(name)
    }

    /**
     * Adds a distinct property projection
     *
     * @param property The property to obtain the distinct value for
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> distinct(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.distinct(property)
        return newCriteria
    }

    /**
     * Method missing handler that deals with the invocation of dynamic finders
     *
     * @param methodName The method name
     * @param args The arguments
     * @return The result of the method call
     */
    @CompileDynamic
    def methodMissing(String methodName, args) {
        initialiseIfNecessary(targetClass)
        def method = dynamicFinders.find { FinderMethod f -> f.isMethodMatch(methodName) }
        if (method != null) {
            applyLazyCriteria()
            return method.invoke(targetClass, methodName,this, args)
        }

        if (!args) {
            throw new MissingMethodException(methodName, AbstractDetachedCriteria, args)
        }

        final prop = persistentEntity.getPropertyByName(methodName)
        if (!(prop instanceof Association)) {
            throw new MissingMethodException(methodName, AbstractDetachedCriteria, args)
        }


        def alias = args[0] instanceof CharSequence ? args[0].toString() : null

        def existing = associationCriteriaMap[methodName]
        alias = !alias && existing ? existing.alias : alias
        DetachedAssociationCriteria associationCriteria = alias ? new DetachedAssociationCriteria(prop.associatedEntity.javaClass, prop, alias)
                : new DetachedAssociationCriteria(prop.associatedEntity.javaClass, prop)

        associationCriteriaMap[methodName] = associationCriteria
        add associationCriteria



        def lastArg = args[-1]
        if(lastArg instanceof Closure) {
            Closure callable = lastArg
            callable.resolveStrategy = Closure.DELEGATE_FIRST

            Closure parentCallable = callable
            while(parentCallable.delegate instanceof Closure) {
                parentCallable = (Closure)parentCallable.delegate
            }

            def previous = parentCallable.delegate

            try {
                parentCallable.delegate = associationCriteria
                callable.call()
            } finally {
                parentCallable.delegate = previous
            }
        }
    }


    protected void handleJunction(Closure callable) {
        try {
            callable.delegate = this
            callable.call()
        }
        finally {
            def lastJunction = junctions.remove(junctions.size() - 1)
            add lastJunction
        }
    }

    protected abstract QueryableCriteria buildQueryableCriteria(Closure queryClosure)


    protected void applyLazyCriteria() {
        if (lazyQuery == null) {
            return
        }

        def criteria = lazyQuery
        lazyQuery = null
        this.with criteria
    }

    @Override
    public Criteria cache(boolean shouldCache) {
        // no-op for now
        this
    }

    @Override
    public Criteria readOnly(boolean readOnly) {
        // no-op for now
        this
    }
}
