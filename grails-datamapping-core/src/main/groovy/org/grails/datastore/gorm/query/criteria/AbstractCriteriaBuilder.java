package org.grails.datastore.gorm.query.criteria;

import grails.gorm.CriteriaBuilder;
import grails.gorm.DetachedCriteria;
import groovy.lang.*;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.QueryCreator;
import org.grails.datastore.mapping.query.Restrictions;
import org.grails.datastore.mapping.query.api.Criteria;
import org.grails.datastore.mapping.query.api.ProjectionList;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.springframework.util.Assert;

import java.util.*;

/**
 * Abstract criteria builder implementation
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractCriteriaBuilder extends GroovyObjectSupport implements Criteria, ProjectionList {
    public static final String ORDER_DESCENDING = "desc";
    public static final String ORDER_ASCENDING = "asc";

    protected static final String ROOT_CALL = "call";
    protected static final String ROOT_DO_CALL = "doCall";
    protected static final String SCROLL_CALL = "scroll";

    protected final Class targetClass;
    protected final QueryCreator queryCreator;
    protected Query query;
    protected boolean uniqueResult = false;
    protected boolean paginationEnabledList;
    protected  List<Query.Order> orderEntries = new ArrayList<Query.Order>();
    protected  MetaObjectProtocol queryMetaClass;
    protected  Query.ProjectionList projectionList;
    protected PersistentEntity persistentEntity;
    protected boolean readOnly;
    private List<Query.Junction> logicalExpressionStack = new ArrayList<Query.Junction>();


    public AbstractCriteriaBuilder(final Class targetClass, QueryCreator queryCreator, final MappingContext mappingContext) {
        Assert.notNull(targetClass, "Argument [targetClass] cannot be null");
        Assert.notNull(mappingContext, "Argument [session] cannot be null");

        persistentEntity = mappingContext.getPersistentEntity(
                targetClass.getName());
        if (persistentEntity == null) {
            throw new IllegalArgumentException("Class [" + targetClass.getName() +
                    "] is not a persistent entity");
        }

        this.targetClass = targetClass;
        this.queryCreator = queryCreator;
    }



    public Class getTargetClass() {
        return this.targetClass;
    }

    public void setUniqueResult(boolean uniqueResult) {
        this.uniqueResult = uniqueResult;
   }

    @Override
   public Criteria cache(boolean cache) {
       query.cache(cache);
       return this;
   }

    @Override
   public Criteria readOnly(boolean readOnly) {
       this.readOnly = readOnly;
       return this;
   }

    public Criteria join(String property) {
      query.join(property);
      return this;
  }

    public Criteria select(String property) {
      query.select(property);
      return this;
  }

    public Query.ProjectionList id() {
       if (projectionList != null) {
           projectionList.id();
       }
       return projectionList;
    }

    /**
     * Count the number of records returned
     * @return The project list
     */

    public Query.ProjectionList count() {
        if (projectionList != null) {
            projectionList.count();
        }
        return projectionList;
    }

    /**
     * Projection that signifies to count distinct results
     *
     * @param property The name of the property
     * @return The projection list
     */
    public ProjectionList countDistinct(String property) {
        if (projectionList != null) {
            projectionList.countDistinct(property);
        }
        return projectionList;
    }

    /**
     * Defines a group by projection for datastores that support it
     *
     * @param property The property name
     *
     * @return The projection list
     */
    @Override
    public ProjectionList groupProperty(String property) {
        if (projectionList != null) {
            projectionList.groupProperty(property);
        }
        return projectionList;
    }

    /**
     * Projection that signifies to return only distinct results
     *
     * @return The projection list
     */
    public ProjectionList distinct() {
        if (projectionList != null) {
            projectionList.distinct();
        }
        return projectionList;
    }

    /**
     * Projection that signifies to return only distinct results
     *
     * @param property The name of the property
     * @return The projection list
     */
    public ProjectionList distinct(String property) {
        if (projectionList != null) {
            projectionList.distinct(property);
        }
        return projectionList;
    }

    /**
     * Count the number of records returned
     * @return The project list
     */
    public ProjectionList rowCount() {
        return count();
    }

    /**
     * A projection that obtains the value of a property of an entity
     * @param name The name of the property
     * @return The projection list
     */
    public ProjectionList property(String name) {
        if (projectionList != null) {
            projectionList.property(name);
        }
        return projectionList;
    }

    /**
     * Computes the sum of a property
     *
     * @param name The name of the property
     * @return The projection list
     */
    public ProjectionList sum(String name) {
        if (projectionList != null) {
            projectionList.sum(name);
        }
        return projectionList;
    }

    /**
     * Computes the min value of a property
     *
     * @param name The name of the property
     * @return The projection list
     */
    public ProjectionList min(String name) {
        if (projectionList != null) {
            projectionList.min(name);
        }
        return projectionList;
    }

    /**
     * Computes the max value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public ProjectionList max(String name) {
        if (projectionList != null) {
            projectionList.max(name);
        }
        return projectionList;
    }

    /**
     * Computes the average value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public ProjectionList avg(String name) {
       if (projectionList != null) {
           projectionList.avg(name);
       }
       return projectionList;
    }

    @Override
    public Object invokeMethod(String name, Object obj) {
        Object[] args = obj.getClass().isArray() ? (Object[])obj : new Object[]{obj};

        ensureQueryIsInitialized();
        if (isCriteriaConstructionMethod(name, args)) {


            uniqueResult = false;

            invokeClosureNode(args[0]);

            Object result;
            if (!uniqueResult) {
                result = invokeList();
            }
            else {
                result = query.singleResult();
            }
            query = null;
            return result;
        }

        MetaMethod metaMethod = getMetaClass().getMetaMethod(name, args);
        if (metaMethod != null) {
            return metaMethod.invoke(this, args);
        }

        metaMethod = queryMetaClass.getMetaMethod(name, args);
        if (metaMethod != null) {
            return metaMethod.invoke(query, args);
        }

        if (args.length == 1 && args[0] instanceof Closure) {

            final PersistentProperty property = persistentEntity.getPropertyByName(name);
            if (property instanceof Association) {
                Association association = (Association) property;
                Query previousQuery = query;
                PersistentEntity previousEntity = persistentEntity;
                List<Query.Junction> previousLogicalExpressionStack = logicalExpressionStack;

                Query associationQuery = null;
                try {
                    associationQuery = query.createQuery(property.getName());
                    if (associationQuery instanceof AssociationQuery) {
                        addToCriteria((Query.Criterion) associationQuery);
                    }
                    query = associationQuery;
                    persistentEntity = association.getAssociatedEntity();
                    logicalExpressionStack = new ArrayList<Query.Junction>();
                    invokeClosureNode(args[0]);
                    return query;
                }
                finally {

                    logicalExpressionStack = previousLogicalExpressionStack;
                    persistentEntity = previousEntity;
                    query = previousQuery;
                }
            }
        }

        throw new MissingMethodException(name, getClass(), args);
    }

    protected Object invokeList() {
        Object result;
        result = query.list();
        return result;
    }

    /**
     * Defines projections
     *
     * @param callable The closure defining the projections
     * @return The projections list
     */
    public ProjectionList projections(Closure callable) {
        projectionList = query.projections();
        invokeClosureNode(callable);
        return projectionList;
    }

    public Criteria and(Closure callable) {
        handleJunction(new Query.Conjunction(), callable);
        return this;
    }

    public Criteria or(Closure callable) {
        handleJunction(new Query.Disjunction(), callable);
        return this;
    }

    public Criteria not(Closure callable) {
        handleJunction(new Query.Negation(), callable);
        return this;
    }

    public Criteria idEquals(Object value) {
        addToCriteria(Restrictions.idEq(value));
        return this;
    }

    @Override
    public Criteria exists(QueryableCriteria<?> subquery) {
        addToCriteria(new Query.Exists(subquery));
        return this;
    }

    @Override
    public Criteria notExists(QueryableCriteria<?> subquery) {
        addToCriteria(new Query.NotExists(subquery));
        return this;
    }

    public Criteria isEmpty(String propertyName) {
        validatePropertyName(propertyName, "isEmpty");
        addToCriteria(Restrictions.isEmpty(propertyName));
        return this;
    }

    public Criteria isNotEmpty(String propertyName) {
        validatePropertyName(propertyName, "isNotEmpty");
        addToCriteria(Restrictions.isNotEmpty(propertyName));
        return this;
    }

    public Criteria isNull(String propertyName) {
        validatePropertyName(propertyName, "isNull");
        addToCriteria(Restrictions.isNull(propertyName));
        return this;
    }

    public Criteria isNotNull(String propertyName) {
        validatePropertyName(propertyName, "isNotNull");
        addToCriteria(Restrictions.isNotNull(propertyName));
        return this;
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria eq(String propertyName, Object propertyValue) {
        validatePropertyName(propertyName, "eq");
        addToCriteria(Restrictions.eq(propertyName, propertyValue));
        return this;
    }

    /**
     * Apply an "equals" constraint to each property in the key set of a <code>Map</code>
     *
     * @param propertyValues a map from property names to values
     *
     * @return Criterion
     *
     * @see Query.Conjunction
     */
    @Override
    public Criteria allEq(Map<String, Object> propertyValues) {
        Query.Conjunction conjunction = new Query.Conjunction();
        for (String property : propertyValues.keySet()) {
            conjunction.add( Restrictions.eq(property, propertyValues.get(property)));
        }
        addToCriteria(conjunction);
        return this;
    }

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria eqAll(String propertyName, Closure propertyValue) {
        return eqAll(propertyName, buildQueryableCriteria(propertyValue));
    }

    @SuppressWarnings("unchecked")
    private QueryableCriteria buildQueryableCriteria(Closure queryClosure) {
        return new DetachedCriteria(targetClass).build(queryClosure);
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria gtAll(String propertyName, Closure propertyValue) {
        return gtAll(propertyName, buildQueryableCriteria(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria ltAll(String propertyName, Closure propertyValue) {
        return ltAll(propertyName, buildQueryableCriteria(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria geAll(String propertyName, Closure propertyValue) {
        return geAll(propertyName, buildQueryableCriteria(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria leAll(String propertyName, Closure propertyValue) {
        return leAll(propertyName, buildQueryableCriteria(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria eqAll(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "eqAll");
        addToCriteria(new Query.EqualsAll(propertyName, propertyValue));
        return this;
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria gtAll(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "gtAll");
        addToCriteria(new Query.GreaterThanAll(propertyName, propertyValue));
        return this;
    }

    @Override
    public Criteria gtSome(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "gtSome");
        addToCriteria(new Query.GreaterThanSome(propertyName, propertyValue));
        return this;
    }

    @Override
    public Criteria gtSome(String propertyName, Closure<?> propertyValue) {
        return gtSome(propertyName, buildQueryableCriteria(propertyValue));
    }

    @Override
    public Criteria geSome(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "geSome");
        addToCriteria(new Query.GreaterThanEqualsSome(propertyName, propertyValue));
        return this;
    }

    @Override
    public Criteria geSome(String propertyName, Closure<?> propertyValue) {
        return geSome(propertyName, buildQueryableCriteria(propertyValue));
    }

    @Override
    public Criteria ltSome(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "ltSome");
        addToCriteria(new Query.LessThanEqualsSome(propertyName, propertyValue));
        return this;
    }

    @Override
    public Criteria ltSome(String propertyName, Closure<?> propertyValue) {
        return ltSome(propertyName, buildQueryableCriteria(propertyValue));
    }

    @Override
    public Criteria leSome(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "leSome");
        addToCriteria(new Query.LessThanEqualsSome(propertyName, propertyValue));
        return this;
    }

    @Override
    public Criteria leSome(String propertyName, Closure<?> propertyValue) {
        return leSome(propertyName, buildQueryableCriteria(propertyValue));
    }

    @Override
    public Criteria in(String propertyName, QueryableCriteria<?> subquery) {
        return inList(propertyName, subquery);
    }

    @Override
    public Criteria in(String propertyName, Closure<?> subquery) {
        return inList(propertyName, subquery);
    }

    @Override
    public Criteria inList(String propertyName, QueryableCriteria<?> subquery) {
        validatePropertyName(propertyName, "inList");
        addToCriteria(new Query.In(propertyName, subquery));
        return this;
    }

    @Override
    public Criteria inList(String propertyName, Closure<?> subquery) {
        return inList(propertyName, buildQueryableCriteria(subquery));
    }

    @Override
    public Criteria notIn(String propertyName, QueryableCriteria<?> subquery) {
        validatePropertyName(propertyName, "notIn");
        addToCriteria(new Query.NotIn(propertyName, subquery));
        return this;
    }

    @Override
    public Criteria notIn(String propertyName, Closure<?> subquery) {
        return notIn(propertyName, buildQueryableCriteria(subquery));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria ltAll(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "ltAll");
        addToCriteria(new Query.LessThanAll(propertyName, propertyValue));
        return this;
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria geAll(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "geAll");
        addToCriteria(new Query.GreaterThanEqualsAll(propertyName, propertyValue));
        return this;
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria leAll(String propertyName, QueryableCriteria propertyValue) {
        validatePropertyName(propertyName, "leAll");
        addToCriteria(new Query.LessThanEqualsAll(propertyName, propertyValue));
        return this;
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria idEq(Object propertyValue) {
        addToCriteria(Restrictions.idEq(propertyValue));
        return this;
    }

    /**
     * Creates a "not equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria ne(String propertyName, Object propertyValue) {
        validatePropertyName(propertyName, "ne");
        addToCriteria(Restrictions.ne(propertyName, propertyValue));
        return this;
    }

    /**
     * Restricts the results by the given property value range (inclusive)
     *
     * @param propertyName The property name
     *
     * @param start The start of the range
     * @param finish The end of the range
     * @return A Criterion instance
     */
    public Criteria between(String propertyName, Object start, Object finish) {
        validatePropertyName(propertyName, "between");
        addToCriteria(Restrictions.between(propertyName, start, finish));
        return this;
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    public Criteria gte(String property, Object value) {
        validatePropertyName(property, "gte");
        addToCriteria(Restrictions.gte(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    public Criteria ge(String property, Object value) {
        gte(property, value);
        return this;
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    public Criteria gt(String property, Object value) {
        validatePropertyName(property, "gt");
        addToCriteria(Restrictions.gt(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    public Criteria lte(String property, Object value) {
        validatePropertyName(property, "lte");
        addToCriteria(Restrictions.lte(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    public Criteria le(String property, Object value) {
        lte(property, value);
        return this;
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     * @param property The property
     * @param value The value
     * @return The Criterion instance
     */
    public Criteria lt(String property, Object value) {
        validatePropertyName(property, "lt");
        addToCriteria(Restrictions.lt(property, value));
        return this;
    }

    /**
     * Creates an like Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria like(String propertyName, Object propertyValue) {
        validatePropertyName(propertyName, "like");
        Assert.notNull(propertyValue, "Cannot use like expression with null value");
        addToCriteria(Restrictions.like(propertyName, propertyValue.toString()));
        return this;
    }

    /**
     * Creates an ilike Criterion based on the specified property name and value. Unlike a like condition, ilike is case insensitive
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria ilike(String propertyName, Object propertyValue) {
        validatePropertyName(propertyName, "ilike");
        Assert.notNull(propertyValue, "Cannot use ilike expression with null value");
        addToCriteria(Restrictions.ilike(propertyName, propertyValue.toString()));
        return this;
    }

    /**
     * Creates an rlike Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria rlike(String propertyName, Object propertyValue) {
        validatePropertyName(propertyName, "like");
        Assert.notNull(propertyValue, "Cannot use like expression with null value");
        addToCriteria(Restrictions.rlike(propertyName, propertyValue.toString()));
        return this;
    }

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return A Criterion instance
     */
    public Criteria in(String propertyName, Collection values) {
        validatePropertyName(propertyName, "in");
        Assert.notNull(values, "Cannot use in expression with null values");
        addToCriteria(Restrictions.in(propertyName, values));
        return this;
    }

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return A Criterion instance
     */
    public Criteria inList(String propertyName, Collection values) {
        in(propertyName, values);
        return this;
    }

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return A Criterion instance
     */
    public Criteria inList(String propertyName, Object[] values) {
        return in(propertyName, Arrays.asList(values));
    }

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param values The values
     *
     * @return A Criterion instance
     */
    public Criteria in(String propertyName, Object[] values) {
        return in(propertyName, Arrays.asList(values));
    }

    public Criteria sizeEq(String propertyName, int size) {
        validatePropertyName(propertyName, "sizeEq");
        addToCriteria(Restrictions.sizeEq(propertyName, size));
        return this;

    }

    public Criteria sizeGt(String propertyName, int size) {
        validatePropertyName(propertyName, "sizeGt");
        addToCriteria(Restrictions.sizeGt(propertyName, size));
        return this;
    }

    public Criteria sizeGe(String propertyName, int size) {
        validatePropertyName(propertyName, "sizeGe");
        addToCriteria(Restrictions.sizeGe(propertyName, size));
        return this;
    }

    public Criteria sizeLe(String propertyName, int size) {
        validatePropertyName(propertyName, "sizeLe");
        addToCriteria(Restrictions.sizeLe(propertyName, size));
        return this;
    }

    public Criteria sizeLt(String propertyName, int size) {
        validatePropertyName(propertyName, "sizeLt");
        addToCriteria(Restrictions.sizeLt(propertyName, size));
        return this;
    }

    public Criteria sizeNe(String propertyName, int size) {
        validatePropertyName(propertyName, "sizeNe");
        addToCriteria(Restrictions.sizeNe(propertyName, size));
        return this;
    }

    /**
     * Constraints a property to be equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    public Criteria eqProperty(String propertyName, String otherPropertyName) {
        validatePropertyName(propertyName, "eqProperty");
        validatePropertyName(otherPropertyName, "eqProperty");
        addToCriteria(Restrictions.eqProperty(propertyName, otherPropertyName));
        return this;
    }

    /**
     * Constraints a property to be not equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    public Criteria neProperty(String propertyName, String otherPropertyName) {
        validatePropertyName(propertyName, "neProperty");
        validatePropertyName(otherPropertyName, "neProperty");
        addToCriteria(Restrictions.neProperty(propertyName, otherPropertyName));
        return this;

    }

    /**
     * Constraints a property to be greater than a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    public Criteria gtProperty(String propertyName, String otherPropertyName) {
        validatePropertyName(propertyName, "gtProperty");
        validatePropertyName(otherPropertyName, "gtProperty");
        addToCriteria(Restrictions.gtProperty(propertyName, otherPropertyName));
        return this;

    }

    /**
     * Constraints a property to be greater than or equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    public Criteria geProperty(String propertyName, String otherPropertyName) {
        validatePropertyName(propertyName, "geProperty");
        validatePropertyName(otherPropertyName, "geProperty");
        addToCriteria(Restrictions.geProperty(propertyName, otherPropertyName));
        return this;
    }

    /**
     * Constraints a property to be less than a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    public Criteria ltProperty(String propertyName, String otherPropertyName) {
        validatePropertyName(propertyName, "ltProperty");
        validatePropertyName(otherPropertyName, "ltProperty");
        addToCriteria(Restrictions.ltProperty(propertyName, otherPropertyName));
        return this;
    }

    /**
     * Constraints a property to be less than or equal to a specified other property
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    public Criteria leProperty(String propertyName, String otherPropertyName) {
        validatePropertyName(propertyName, "leProperty");
        validatePropertyName(otherPropertyName, "leProperty");
        addToCriteria(Restrictions.leProperty(propertyName, otherPropertyName));
        return this;
    }

    /**
     * Orders by the specified property name (defaults to ascending)
     *
     * @param propertyName The property name to order by
     * @return A Order instance
     */
    public Criteria order(String propertyName) {
        Query.Order o = Query.Order.asc(propertyName);
        if (paginationEnabledList) {
            orderEntries.add(o);
        }
        else {
            query.order(o);
        }
        return this;
    }

    /**
     * Orders by the specified property name (defaults to ascending)
     *
     * @param o The order object
     * @return This criteria
     */
    @Override
    public Criteria order(Query.Order o) {
        if (paginationEnabledList) {
            orderEntries.add(o);
        }
        else {
            query.order(o);
        }
        return this;
    }

    /**
     * Orders by the specified property name and direction
     *
     * @param propertyName The property name to order by
     * @param direction Either "asc" for ascending or "desc" for descending
     *
     * @return A Order instance
     */
    public Criteria order(String propertyName, String direction) {
        Query.Order o;
        if (direction.equals(CriteriaBuilder.ORDER_DESCENDING)) {
            o = Query.Order.desc(propertyName);
        }
        else {
            o = Query.Order.asc(propertyName);
        }
        if (paginationEnabledList) {
            orderEntries.add(o);
        }
        else {
            query.order(o);
        }
        return this;
    }

    protected void validatePropertyName(String propertyName, String methodName) {
        if (persistentEntity == null) return;
        if (propertyName == null) {
            throw new IllegalArgumentException("Cannot use [" + methodName +
                    "] restriction with null property name");
        }

        PersistentProperty property = persistentEntity.getPropertyByName(propertyName);
        if (property == null && persistentEntity.getIdentity().getName().equals(propertyName)) {
            property = persistentEntity.getIdentity();
        }
        if (property == null && !queryCreator.isSchemaless()) {
            throw new IllegalArgumentException("Property [" + propertyName +
                    "] is not a valid property of class [" + persistentEntity + "]");
        }
    }

    protected void ensureQueryIsInitialized() {
    	if(query == null) {
    		query = queryCreator.createQuery(targetClass);
    	}
    	if(queryMetaClass == null) {
    		queryMetaClass = GroovySystem.getMetaClassRegistry().getMetaClass(query.getClass());
    	}
    }

    private boolean isCriteriaConstructionMethod(String name, Object[] args) {
        return (name.equals(CriteriaBuilder.ROOT_CALL) ||
                name.equals(CriteriaBuilder.ROOT_DO_CALL) ||
                name.equals(CriteriaBuilder.SCROLL_CALL) && args.length == 1 && args[0] instanceof Closure);
    }

    protected void invokeClosureNode(Object args) {
        if (args instanceof Closure) {
            Closure callable = (Closure)args;
            callable.setDelegate(this);
            callable.setResolveStrategy(Closure.DELEGATE_FIRST);
            callable.call();
        }
    }

    private void handleJunction(Query.Junction junction, Closure callable) {
        logicalExpressionStack.add(junction);
        try {
            if (callable != null) {
                invokeClosureNode(callable);
            }
        } finally {
            Query.Junction logicalExpression = logicalExpressionStack.remove(logicalExpressionStack.size()-1);
            addToCriteria(logicalExpression);
        }
    }

    /*
        * adds and returns the given criterion to the currently active criteria set.
        * this might be either the root criteria or a currently open
        * LogicalExpression.
        */
    protected Query.Criterion addToCriteria(Query.Criterion c) {
        if (c instanceof Query.PropertyCriterion) {
            Query.PropertyCriterion pc = (Query.PropertyCriterion) c;

            Object value = pc.getValue();
            if (value instanceof Closure) {
                pc.setValue(buildQueryableCriteria((Closure) value));
            }
        }
        if (!logicalExpressionStack.isEmpty()) {
            logicalExpressionStack.get(logicalExpressionStack.size() - 1).add(c);
        }
        else {
            if (query == null) {
                ensureQueryIsInitialized();
            }
            query.add(c);
        }
        return c;
    }

    public Query getQuery() {
        return query;
    }

    public void build(Closure criteria) {
        if (criteria != null) {
            invokeClosureNode(criteria);
        }
    }
}
