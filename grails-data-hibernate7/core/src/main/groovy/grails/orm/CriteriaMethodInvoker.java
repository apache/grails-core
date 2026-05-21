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
package grails.orm;

import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.MetaMethod;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;

import org.springframework.beans.BeanUtils;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.query.PagedResultList;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.grails.orm.hibernate.query.HibernateQueryArgument;

/**
 * If you want to extend functionality of the HibernateCriteriaBuilder
 * extend this class and override the methods you want
 */
public class CriteriaMethodInvoker {

    private static final Object UNHANDLED = new Object();

    private final HibernateCriteriaBuilder builder;

    public CriteriaMethodInvoker(HibernateCriteriaBuilder builder) {
        this.builder = builder;
    }

    public Object invokeMethod(String name, Object... args) {
        CriteriaMethods method = CriteriaMethods.fromName(name);

        Object result = tryCriteriaConstruction(method, args);
        if (result != UNHANDLED) return result;

        result = tryMetaMethod(name, args);
        if (result != UNHANDLED) return result;

        result = tryAssociationOrJunction(name, method, args);
        if (result != UNHANDLED) return result;

        result = trySimpleCriteria(name, method, args);
        if (result != UNHANDLED) return result;

        result = tryPropertyCriteria(method, args);
        if (result != UNHANDLED) return result;

        return CriteriaMethods.fromName(name, HibernateCriteriaBuilder.class, args);
    }

    protected Object tryCriteriaConstruction(CriteriaMethods method, Object... args) {
        if (method == null || !isCriteriaConstructionMethod(method, args)) {
            return UNHANDLED;
        }

        HibernateQuery hibernateQuery = builder.getHibernateQuery();
        switch (method) {
            case GET_CALL -> builder.setUniqueResult(true);
            case SCROLL_CALL -> builder.setScroll(true);
            case COUNT_CALL -> builder.setCount(true);
            case LIST_DISTINCT_CALL -> builder.setDistinct(true);
            default -> { }
        }

        // Check for pagination params
        if (method == CriteriaMethods.LIST_CALL && args.length == 2) {
            builder.setPaginationEnabledList(true);
            if (args[0] instanceof Map<?, ?> map) {
                if (map.get("max") instanceof Number max) {
                    hibernateQuery.maxResults(max.intValue());
                }
                if (map.get("offset") instanceof Number offset) {
                    hibernateQuery.firstResult(offset.intValue());
                }
            }
            invokeClosureNode(args[1]);
        } else {
            invokeClosureNode(args[0]);
        }

        Object result;
        if (!builder.isUniqueResult()) {
            if (builder.isDistinct()) {
                hibernateQuery.distinct();
                result = hibernateQuery.list();
            } else if (builder.isCount()) {
                hibernateQuery.projections().count();
                result = hibernateQuery.singleResult();
            } else if (builder.isPaginationEnabledList()) {
                Map<?, ?> argMap = (Map<?, ?>) args[0];
                final String sortField = (String) argMap.get(HibernateQueryArgument.SORT.value());
                if (sortField != null) {
                    final boolean ignoreCase =
                            !(argMap.get(HibernateQueryArgument.IGNORE_CASE.value()) instanceof Boolean b) || b;
                    final String orderParam = (String) argMap.get(HibernateQueryArgument.ORDER.value());
                    final Query.Order.Direction direction =
                            Query.Order.Direction.DESC.name().equalsIgnoreCase(orderParam) ?
                                    Query.Order.Direction.DESC :
                                    Query.Order.Direction.ASC;
                    Query.Order order;
                    order = new Query.Order(sortField, direction);
                    if (ignoreCase) {
                        order.ignoreCase();
                    }
                    hibernateQuery.order(order);
                }
                result = new PagedResultList(hibernateQuery);
            } else if (builder.isScroll()) {
                result = hibernateQuery.scroll();
            } else {
                result = hibernateQuery.list();
            }
        } else {
            result = hibernateQuery.singleResult();
        }
        if (!builder.isParticipate()) {
            builder.closeSession();
        }
        return result;
    }

    protected Object tryMetaMethod(String name, Object... args) {
        MetaMethod metaMethod = builder.getMetaClass().getMetaMethod(name, args);
        if (metaMethod != null) {
            return metaMethod.invoke(builder, args);
        }
        return UNHANDLED;
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    protected Object tryAssociationOrJunction(String name, CriteriaMethods method, Object... args) {
        if (!isAssociationQueryMethod(args) && !isAssociationQueryWithJoinSpecificationMethod(args)) {
            return UNHANDLED;
        }

        final boolean hasMoreThanOneArg = args.length > 1;
        final Closure<?> callable = hasMoreThanOneArg ? (Closure<?>) args[1] : (Closure<?>) args[0];
        final HibernateQuery hibernateQuery = builder.getHibernateQuery();

        if (method != null) {
            switch (method) {
                case AND:
                    hibernateQuery.and(callable);
                    return name;
                case OR:
                    hibernateQuery.or(callable);
                    return name;
                case NOT:
                    hibernateQuery.not(callable);
                    return name;
                case PROJECTIONS:
                    if (args.length == 1 && (args[0] instanceof Closure)) {
                        invokeClosureNode(callable);
                        return name;
                    }
                    break;
                default:
                    break;
            }
        }

        final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(builder.getTargetClass(), name);
        if (pd != null && pd.getReadMethod() != null) {
            final Metamodel metamodel = builder.getSessionFactory().getMetamodel();
            final EntityType<?> entityType = metamodel.entity(builder.getTargetClass());
            final Attribute<?, ?> attribute = entityType.getAttribute(name);

            if (attribute.isAssociation()) {
                Class<?> oldTargetClass = builder.getTargetClass();
                Class<?> associationClass = builder.getClassForAssociationType(attribute);
                builder.setTargetClass(associationClass);
                JoinType joinType;
                if (hasMoreThanOneArg) {
                    joinType = builder.convertFromInt((Integer) args[0]);
                } else if (associationClass.equals(oldTargetClass)) {
                    joinType = JoinType.LEFT; // default to left join if joining on the same table
                } else {
                    joinType = builder.convertFromInt(0);
                }

                hibernateQuery.join(name, joinType);

                GrailsHibernatePersistentEntity parentEntity = (GrailsHibernatePersistentEntity)
                        hibernateQuery.getSession().getMappingContext().getPersistentEntity(oldTargetClass.getName());
                PersistentProperty<?> property = parentEntity.getPropertyByName(name);
                if (property instanceof Association<?> association) {
                    DetachedAssociationCriteria<?> associationCriteria =
                            new DetachedAssociationCriteria<>(associationClass, association);
                    DetachedCriteria<?> oldDetachedCriteria = hibernateQuery.getDetachedCriteria();
                    hibernateQuery.setDetachedCriteria(associationCriteria);
                    try {
                        invokeClosureNode(callable);
                    } finally {
                        hibernateQuery.setDetachedCriteria(oldDetachedCriteria);
                    }
                    hibernateQuery.add((Query.Criterion) associationCriteria);
                } else {
                    // Fallback for non-GORM associations if any
                    hibernateQuery.in(name, new DetachedCriteria<>(associationClass).build(callable));
                }

                builder.setTargetClass(oldTargetClass);

                return name;
            }
        }
        return UNHANDLED;
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    protected Object trySimpleCriteria(String name, CriteriaMethods method, Object... args) {
        if (method != null) {
            switch (method) {
                case ID_EQUALS:
                    if (args.length == 1 && args[0] != null) {
                        return builder.eq("id", args[0]);
                    }
                    break;
                case CACHE:
                    if (args.length == 1 && args[0] instanceof Boolean b) {
                        builder.cache(b);
                        return name;
                    }
                    break;
                case READ_ONLY:
                    if (args.length == 1 && args[0] instanceof Boolean b) {
                        builder.readOnly(b);
                        return name;
                    }
                    break;
                case SINGLE_RESULT:
                    return builder.singleResult();
                case CREATE_ALIAS:
                    if (args.length == 2 && args[0] instanceof String s && args[1] instanceof String a) {
                        return builder.createAlias(s, a);
                    } else if (args.length == 3 &&
                            args[0] instanceof String s &&
                            args[1] instanceof String a &&
                            args[2] instanceof Number jt) {
                        builder.createAlias(s, a, jt.intValue());
                        return builder;
                    }
                    return name;
                case IS_NULL, IS_NOT_NULL, IS_EMPTY, IS_NOT_EMPTY:
                    if (args.length == 1 && args[0] instanceof String value) {
                        switch (method) {
                            case IS_NULL -> builder.getHibernateQuery().isNull(value);
                            case IS_NOT_NULL -> builder.getHibernateQuery().isNotNull(value);
                            case IS_EMPTY -> builder.getHibernateQuery().isEmpty(value);
                            case IS_NOT_EMPTY -> builder.getHibernateQuery().isNotEmpty(value);
                            default -> { }
                        }
                        return name;
                    } else if (args.length == 1 && args[0] != null) {
                        builder.throwRuntimeException(new IllegalArgumentException(
                                "call to [" + name + "] with value [" + args[0] + "] requires a String value."));
                    }
                    break;
                default:
                    break;
            }
        }
        return UNHANDLED;
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    protected Object tryPropertyCriteria(CriteriaMethods method, Object... args) {
        if (method == CriteriaMethods.FETCH_MODE) {
            if (args.length == 2 && args[0] instanceof String s && args[1] instanceof org.hibernate.FetchMode fm) {
                builder.fetchMode(s, fm);
                return "fetchMode";
            }
        }

        if (method == null || args.length < 2 || !(args[0] instanceof String propertyName)) {
            return UNHANDLED;
        }

        switch (method) {
            case RLIKE:
                return builder.rlike(propertyName, args[1]);
            case BETWEEN:
                if (args.length >= 3) {
                    return builder.between(propertyName, args[1], args[2]);
                }
                break;
            case EQUALS:
                if (args.length == 3 && args[2] instanceof Map<?, ?> map) {
                    return builder.eq(propertyName, args[1], map);
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
                    return builder.in(propertyName, (Collection<?>) args[1]);
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
            default:
                break;
        }
        return UNHANDLED;
    }

    protected boolean isAssociationQueryMethod(Object... args) {
        return args.length == 1 && args[0] instanceof Closure;
    }

    protected boolean isAssociationQueryWithJoinSpecificationMethod(Object... args) {
        return args.length == 2 && (args[0] instanceof Number) && (args[1] instanceof Closure);
    }

    protected boolean isCriteriaConstructionMethod(CriteriaMethods method, Object... args) {
        return (method == CriteriaMethods.LIST_CALL &&
                        args.length == 2 &&
                        args[0] instanceof Map<?, ?> &&
                        args[1] instanceof Closure) ||
                (method == CriteriaMethods.ROOT_CALL ||
                        method == CriteriaMethods.ROOT_DO_CALL ||
                        method == CriteriaMethods.LIST_CALL ||
                        method == CriteriaMethods.LIST_DISTINCT_CALL ||
                        method == CriteriaMethods.GET_CALL ||
                        method == CriteriaMethods.COUNT_CALL ||
                        (method == CriteriaMethods.SCROLL_CALL && args.length == 1 && args[0] instanceof Closure));
    }

    protected void invokeClosureNode(Object args) {
        Closure<?> callable = (Closure<?>) args;
        callable.setDelegate(builder);
        callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
    }
}
