package grails.orm;

import grails.gorm.DetachedCriteria;
import grails.gorm.PagedResultList;
import groovy.lang.Closure;
import groovy.lang.MetaMethod;
import groovy.lang.MissingMethodException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.grails.orm.hibernate.query.HibernateQueryConstants;
import org.hibernate.SessionFactory;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.Map;

/**
 * Helper class to handle method invocation for HibernateCriteriaBuilder.
 */
public class CriteriaMethodInvoker {

    public static final String AND = "and"; // builder
    public static final String IS_NULL = "isNull"; // builder
    public static final String IS_NOT_NULL = "isNotNull"; // builder
    public static final String NOT = "not";// builder
    public static final String OR = "or"; // builder
    public static final String ID_EQUALS = "idEq"; // builder
    public static final String IS_EMPTY = "isEmpty"; //builder
    public static final String IS_NOT_EMPTY = "isNotEmpty"; //builder
    public static final String RLIKE = "rlike";//method
    public static final String BETWEEN = "between";//method
    public static final String EQUALS = "eq";//method
    public static final String EQUALS_PROPERTY = "eqProperty";//method
    public static final String GREATER_THAN = "gt";//method
    public static final String GREATER_THAN_PROPERTY = "gtProperty";//method
    public static final String GREATER_THAN_OR_EQUAL = "ge";//method
    public static final String GREATER_THAN_OR_EQUAL_PROPERTY = "geProperty";//method
    public static final String ILIKE = "ilike";//method
    public static final String IN = "in";//method
    public static final String LESS_THAN = "lt"; //method
    public static final String LESS_THAN_PROPERTY = "ltProperty";//method
    public static final String LESS_THAN_OR_EQUAL = "le";//method
    public static final String LESS_THAN_OR_EQUAL_PROPERTY = "leProperty";//method
    public static final String LIKE = "like";//method
    public static final String NOT_EQUAL = "ne";//method
    public static final String NOT_EQUAL_PROPERTY = "neProperty";//method
    public static final String SIZE_EQUALS = "sizeEq"; //method
    protected static final String ROOT_DO_CALL = "doCall";
    protected static final String ROOT_CALL = "call";
    protected static final String LIST_CALL = "list";
    protected static final String LIST_DISTINCT_CALL = "listDistinct";
    protected static final String COUNT_CALL = "count";
    protected static final String GET_CALL = "get";
    protected static final String SCROLL_CALL = "scroll";
    protected static final String PROJECTIONS = "projections";

    private final HibernateCriteriaBuilder builder;

    public CriteriaMethodInvoker(HibernateCriteriaBuilder builder) {
        this.builder = builder;
    }

    public Object invokeMethod(String name, Object[] args) {
        HibernateQuery hibernateQuery = builder.getHibernateQuery();

        if (isCriteriaConstructionMethod(name, args)) {
            if (name.equals(GET_CALL)) {
                builder.setUniqueResult(true);
            }
            else if (name.equals(SCROLL_CALL)) {
                builder.setScroll(true);
            }
            else if (name.equals(COUNT_CALL)) {
                builder.setCount(true);
            }
            else if (name.equals(LIST_DISTINCT_CALL)) {
                builder.setDistinct(true);
            }


            // Check for pagination params
            if (name.equals(LIST_CALL) && args.length == 2) {
                builder.setPaginationEnabledList(true);
                if (args[0] instanceof Map map ) {
                    if (map.get("max") instanceof Number max) {
                        hibernateQuery.maxResults(max.intValue());
                    }
                    if (map.get("offset") instanceof Number offset) {
                        hibernateQuery.firstResult(offset.intValue());
                    }
                }
                invokeClosureNode(args[1]);
            }
            else {
                invokeClosureNode(args[0]);
            }

            Object result;
            if (!builder.isUniqueResult()) {
                if (builder.isDistinct()) {
                    hibernateQuery.distinct();
                    result = hibernateQuery.list();
                }
                else if (builder.isCount()) {
                    hibernateQuery.projections().count();
                    result = hibernateQuery.singleResult();
                }
                else if (builder.isPaginationEnabledList()) {
                    Map argMap = (Map)args[0];
                    final String sortField = (String) argMap.get(HibernateQueryConstants.ARGUMENT_SORT);
                    if (sortField != null) {
                        boolean ignoreCase = true;
                        Object caseArg = argMap.get(HibernateQueryConstants.ARGUMENT_IGNORE_CASE);
                        if (caseArg instanceof Boolean) {
                            ignoreCase = (Boolean) caseArg;
                        }
                        final String orderParam = (String) argMap.get(HibernateQueryConstants.ARGUMENT_ORDER);
                        final Query.Order.Direction direction = Query.Order.Direction.DESC.name().equalsIgnoreCase(orderParam) ? Query.Order.Direction.DESC : Query.Order.Direction.ASC;
                        Query.Order order ;
                        if (ignoreCase) {
                            order = new Query.Order(sortField, direction);
                            order.ignoreCase();
                        } else {
                            order = new Query.Order(sortField, direction);
                        }
                        hibernateQuery.order(order);
                    }
                    result = new PagedResultList<>(hibernateQuery);
                }
                else {
                    result = hibernateQuery.list();
                }
            }
            else {
                result = hibernateQuery.singleResult();
            }
            if (!builder.isParticipate()) {
                builder.closeSession();
            }
            return result;
        }


        MetaMethod metaMethod = builder.getMetaClass().getMetaMethod(name, args);
        if (metaMethod != null) {
            return metaMethod.invoke(builder, args);
        }



        if (isAssociationQueryMethod(args) || isAssociationQueryWithJoinSpecificationMethod(args)) {
            final boolean hasMoreThanOneArg = args.length > 1;
            Closure callable = hasMoreThanOneArg ? (Closure) args[1] : (Closure) args[0];
            JoinType joinType = hasMoreThanOneArg ? builder.convertFromInt((Integer)args[0]) : builder.convertFromInt(0);

            if (name.equals(AND)) {
                hibernateQuery.and(callable);
                return name;
            }

            if (name.equals(OR) ) {
                hibernateQuery.or(callable);
                return name;
            }

            if ( name.equals(NOT)) {
                hibernateQuery.not(callable);
                return name;
            }


            if (name.equals(PROJECTIONS) && args.length == 1 && (args[0] instanceof Closure)) {
                invokeClosureNode(callable);
                return name;
            }

            final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(builder.getTargetClass(), name);
            if (pd != null && pd.getReadMethod() != null) {
                final Metamodel metamodel = builder.getSessionFactory().getMetamodel();
                final EntityType<?> entityType = metamodel.entity(builder.getTargetClass());
                final Attribute<?, ?> attribute = entityType.getAttribute(name);

                if (attribute.isAssociation()) {
                    Class oldTargetClass = builder.getTargetClass();
                    builder.setTargetClass(builder.getClassForAssociationType(attribute));
                    if (builder.getTargetClass().equals(oldTargetClass) && !hasMoreThanOneArg) {
                        joinType = JoinType.LEFT; // default to left join if joining on the same table
                    }

                    hibernateQuery.join(name,joinType);
                    hibernateQuery.in(name, new DetachedCriteria(builder.getTargetClass()).build(callable));
                    builder.setTargetClass(oldTargetClass);

                    return name;
                }
            }
        }
        else if (args.length == 1 && args[0] != null) {
            Object value = args[0];
            if (name.equals(ID_EQUALS)) {
                return builder.eq("id", value);
            }
            if (name.equals(IS_NULL) ||
                    name.equals(IS_NOT_NULL) ||
                    name.equals(IS_EMPTY) ||
                    name.equals(IS_NOT_EMPTY)) {
                if (!(value instanceof String)) {
                    builder.throwRuntimeException(new IllegalArgumentException("call to [" + name + "] with value [" +
                            value + "] requires a String value."));
                }
                String propertyName = builder.calculatePropertyName((String)value);
                if (name.equals(IS_NULL)) {
                    hibernateQuery.isNull(propertyName);
                }
                else if (name.equals(IS_NOT_NULL)) {
                    hibernateQuery.isNotNull(propertyName);
                }
                else if (name.equals(IS_EMPTY)) {
                    hibernateQuery.isEmpty(propertyName);
                }
                else {
                    hibernateQuery.isNotEmpty(propertyName);
                }
                return name;
            }
        }
        else if (args.length >= 2 && args[0] instanceof String propertyName) {
            propertyName = builder.calculatePropertyName(propertyName);
            switch (name) {
                case RLIKE:
                    return builder.rlike(propertyName, args[1]);
                case BETWEEN:
                    if (args.length >= 3) {
                        return builder.between(propertyName, args[1], args[2]);
                    }
                    break;
                case EQUALS:
                    if (args.length == 3 && args[2] instanceof Map) {
                        return builder.eq(propertyName, args[1], (Map) args[2]);
                    }
                    return builder.eq(propertyName, args[1]);
                case EQUALS_PROPERTY:
                    return builder.eqProperty(propertyName, args[1].toString());
                case GREATER_THAN:
                    return builder.gt(propertyName, args[1]);
                case GREATER_THAN_PROPERTY:
                    return builder.gtProperty(propertyName, args[1].toString());
                case GREATER_THAN_OR_EQUAL:
                    return builder.ge(propertyName, args[1]);
                case GREATER_THAN_OR_EQUAL_PROPERTY:
                    return builder.geProperty(propertyName, args[1].toString());
                case ILIKE:
                    return builder.ilike(propertyName, args[1]);
                case IN:
                    if (args[1] instanceof Collection) {
                        return builder.in(propertyName, (Collection) args[1]);
                    } else if (args[1] instanceof Object[]) {
                        return builder.in(propertyName, (Object[]) args[1]);
                    }
                    break;
                case LESS_THAN:
                    return builder.lt(propertyName, args[1]);
                case LESS_THAN_PROPERTY:
                    return builder.ltProperty(propertyName, args[1].toString());
                case LESS_THAN_OR_EQUAL:
                    return builder.le(propertyName, args[1]);
                case LESS_THAN_OR_EQUAL_PROPERTY:
                    return builder.leProperty(propertyName, args[1].toString());
                case LIKE:
                    return builder.like(propertyName, args[1]);
                case NOT_EQUAL:
                    return builder.ne(propertyName, args[1]);
                case NOT_EQUAL_PROPERTY:
                    return builder.neProperty(propertyName, args[1].toString());
                case SIZE_EQUALS:
                    if (args[1] instanceof Number) {
                        return builder.sizeEq(propertyName, ((Number) args[1]).intValue());
                    }
                    break;
            }
        }
        throw new MissingMethodException(name, HibernateCriteriaBuilder.class, args);
    }

    private boolean isAssociationQueryMethod(Object[] args) {
        return args.length == 1 && args[0] instanceof Closure;
    }

    private boolean isAssociationQueryWithJoinSpecificationMethod(Object[] args) {
        return args.length == 2 && (args[0] instanceof Number) && (args[1] instanceof Closure);
    }


    private boolean isCriteriaConstructionMethod(String name, Object[] args) {
        return (name.equals(LIST_CALL) && args.length == 2 && args[0] instanceof Map && args[1] instanceof Closure) ||
                (name.equals(ROOT_CALL) ||
                        name.equals(ROOT_DO_CALL) ||
                        name.equals(LIST_CALL) ||
                        name.equals(LIST_DISTINCT_CALL) ||
                        name.equals(GET_CALL) ||
                        name.equals(COUNT_CALL) ||
                        name.equals(SCROLL_CALL) && args.length == 1 && args[0] instanceof Closure);
    }

    private void invokeClosureNode(Object args) {
        Closure<?> callable = (Closure<?>)args;
        callable.setDelegate(builder);
        callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
    }
}
