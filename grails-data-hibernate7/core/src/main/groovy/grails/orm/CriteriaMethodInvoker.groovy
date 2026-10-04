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

import java.beans.PropertyDescriptor

import groovy.transform.CompileStatic
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.Metamodel

import org.springframework.beans.BeanUtils

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.query.HibernatePagedResultList
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.HibernateQueryArgument

/**
 * If you want to extend functionality of the HibernateCriteriaBuilder
 * extend this class and override the methods you want
 */
@CompileStatic
class CriteriaMethodInvoker {

    private static final Object UNHANDLED = new Object()

    private final HibernateCriteriaBuilder builder

    CriteriaMethodInvoker(HibernateCriteriaBuilder builder) {
        this.builder = builder
    }

    Object invokeMethod(String name, Object... args) {
        CriteriaMethods method = CriteriaMethods.fromName(name)

        Object result = tryCriteriaConstruction(method, args)
        if (!UNHANDLED.is(result)) {
            return result
        }

        result = tryMetaMethod(name, args)
        if (!UNHANDLED.is(result)) {
            return result
        }

        result = tryAssociationOrJunction(name, method, args)
        if (!UNHANDLED.is(result)) {
            return result
        }

        result = trySimpleCriteria(name, method, args)
        if (!UNHANDLED.is(result)) {
            return result
        }

        result = tryPropertyCriteria(method, args)
        if (!UNHANDLED.is(result)) {
            return result
        }

        return CriteriaMethods.fromName(name, HibernateCriteriaBuilder, args)
    }

    protected Object tryCriteriaConstruction(CriteriaMethods method, Object... args) {
        if (method == null || !isCriteriaConstructionMethod(method, args)) {
            return UNHANDLED
        }

        HibernateQuery hibernateQuery = builder.getHibernateQuery()
        switch (method) {
            case CriteriaMethods.GET_CALL:
                builder.setUniqueResult(true)
                break
            case CriteriaMethods.SCROLL_CALL:
                builder.setScroll(true)
                break
            case CriteriaMethods.COUNT_CALL:
                builder.setCount(true)
                break
            case CriteriaMethods.LIST_DISTINCT_CALL:
                builder.setDistinct(true)
                break
            default:
                break
        }

        // Check for pagination params
        if (method == CriteriaMethods.LIST_CALL && args.length == 2) {
            builder.setPaginationEnabledList(true)
            if (args[0] instanceof Map) {
                Map map = (Map) args[0]
                Object maxValue = map.get('max')
                if (maxValue instanceof Number) {
                    hibernateQuery.maxResults(maxValue.intValue())
                }
                Object offsetValue = map.get('offset')
                if (offsetValue instanceof Number) {
                    hibernateQuery.firstResult(offsetValue.intValue())
                }
            }
            invokeClosureNode(args[1])
        }
        else {
            invokeClosureNode(args[0])
        }

        Object result
        if (!builder.isUniqueResult()) {
            if (builder.isDistinct()) {
                hibernateQuery.distinct()
                result = hibernateQuery.list()
            }
            else if (builder.isCount()) {
                hibernateQuery.projections().count()
                result = hibernateQuery.singleResult()
            }
            else if (builder.isPaginationEnabledList()) {
                Map argMap = (Map) args[0]
                // the same checks list() and the dynamic finders apply, so sort arguments taken
                // from request parameters fail the same way on every entry point; the direction
                // is checked even without a sort key rather than being silently ignored
                final String direction = DynamicFinder.normalizeDirection(
                        (String) argMap.get(HibernateQueryArgument.ORDER.value()))
                final String sortField = (String) argMap.get(HibernateQueryArgument.SORT.value())
                if (sortField != null) {
                    Object ignoreCaseValue = argMap.get(HibernateQueryArgument.IGNORE_CASE.value())
                    final boolean ignoreCase = !(ignoreCaseValue instanceof Boolean) || (Boolean) ignoreCaseValue
                    DynamicFinder.validateSortProperty(hibernateQuery.getEntity(), sortField)
                    final Query.Order order = DynamicFinder.ORDER_DESC.equals(direction) ?
                            Query.Order.desc(sortField) :
                            Query.Order.asc(sortField)
                    if (ignoreCase) {
                        order.ignoreCase()
                    }
                    hibernateQuery.order(order)
                }
                result = new HibernatePagedResultList(hibernateQuery)
            }
            else if (builder.isScroll()) {
                result = hibernateQuery.scroll()
            }
            else {
                result = hibernateQuery.list()
            }
        }
        else {
            result = hibernateQuery.singleResult()
        }
        if (!builder.isParticipate()) {
            builder.closeSession()
        }
        return result
    }

    protected Object tryMetaMethod(String name, Object... args) {
        MetaMethod metaMethod = builder.getMetaClass().getMetaMethod(name, args)
        if (metaMethod != null) {
            return metaMethod.invoke(builder, args)
        }
        return UNHANDLED
    }

    protected Object tryAssociationOrJunction(String name, CriteriaMethods method, Object... args) {
        if (!isAssociationQueryMethod(args) && !isAssociationQueryWithJoinSpecificationMethod(args)) {
            return UNHANDLED
        }

        final boolean hasMoreThanOneArg = args.length > 1
        final Closure<?> callable = hasMoreThanOneArg ? (Closure<?>) args[1] : (Closure<?>) args[0]
        final HibernateQuery hibernateQuery = builder.getHibernateQuery()

        if (method != null) {
            switch (method) {
                case CriteriaMethods.AND:
                    hibernateQuery.and(callable)
                    return name
                case CriteriaMethods.OR:
                    hibernateQuery.or(callable)
                    return name
                case CriteriaMethods.NOT:
                    hibernateQuery.not(callable)
                    return name
                case CriteriaMethods.PROJECTIONS:
                    if (args.length == 1 && (args[0] instanceof Closure)) {
                        invokeClosureNode(callable)
                        return name
                    }
                    break
                default:
                    break
            }
        }

        final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(builder.getTargetClass(), name)
        if (pd != null && pd.getReadMethod() != null) {
            final Metamodel metamodel = builder.getSessionFactory().getMetamodel()
            final EntityType<?> entityType = metamodel.entity(builder.getTargetClass())
            final Attribute<?, ?> attribute = entityType.getAttribute(name)
            // The JPA metamodel does not consider an embedded component an association, but the
            // GORM model does - an embedded block must build a DetachedAssociationCriteria so its
            // properties resolve against the component. It needs no join: its columns live in the
            // owning entity's table, so an explicit join-type argument on the block is
            // intentionally ignored.
            final boolean embedded =
                    attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED

            if (attribute.isAssociation() || embedded) {
                Class<?> oldTargetClass = builder.getTargetClass()
                Class<?> associationClass = builder.getClassForAssociationType(attribute)
                builder.setTargetClass(associationClass)
                if (!embedded) {
                    JoinType joinType
                    if (hasMoreThanOneArg) {
                        joinType = builder.convertFromInt((Integer) args[0])
                    }
                    else if (associationClass == oldTargetClass) {
                        joinType = JoinType.LEFT // default to left join if joining on the same table
                    }
                    else {
                        joinType = builder.convertFromInt(0)
                    }

                    hibernateQuery.join(name, joinType)
                }

                def parentPersistentEntity = hibernateQuery.getSession().getMappingContext().getPersistentEntity(oldTargetClass.getName())
                GrailsHibernatePersistentEntity parentEntity = (GrailsHibernatePersistentEntity) parentPersistentEntity
                PersistentProperty<?> property = parentEntity.getPropertyByName(name)
                if (property instanceof Association) {
                    Association<?> association = (Association<?>) property
                    DetachedAssociationCriteria<?> associationCriteria =
                            new DetachedAssociationCriteria<>(associationClass, association)
                    DetachedCriteria<?> oldDetachedCriteria = hibernateQuery.getDetachedCriteria()
                    hibernateQuery.setDetachedCriteria(associationCriteria)
                    try {
                        invokeClosureNode(callable)
                    }
                    finally {
                        hibernateQuery.setDetachedCriteria(oldDetachedCriteria)
                    }
                    hibernateQuery.add((Query.Criterion) associationCriteria)
                }
                else {
                    // Fallback for non-GORM associations if any
                    hibernateQuery.in(name, new DetachedCriteria<>(associationClass).build(callable))
                }

                builder.setTargetClass(oldTargetClass)

                return name
            }
        }
        return UNHANDLED
    }

    protected Object trySimpleCriteria(String name, CriteriaMethods method, Object... args) {
        if (method != null) {
            switch (method) {
                case CriteriaMethods.ID_EQUALS:
                    if (args.length == 1 && args[0] != null) {
                        return builder.eq('id', args[0])
                    }
                    break
                case CriteriaMethods.CACHE:
                    if (args.length == 1 && args[0] instanceof Boolean) {
                        builder.cache((Boolean) args[0])
                        return name
                    }
                    break
                case CriteriaMethods.READ_ONLY:
                    if (args.length == 1 && args[0] instanceof Boolean) {
                        builder.readOnly((Boolean) args[0])
                        return name
                    }
                    break
                case CriteriaMethods.SINGLE_RESULT:
                    return builder.singleResult()
                case CriteriaMethods.CREATE_ALIAS:
                    if (args.length == 2 && args[0] instanceof String && args[1] instanceof String) {
                        return builder.createAlias((String) args[0], (String) args[1])
                    }
                    else if (args.length == 3 &&
                            args[0] instanceof String &&
                            args[1] instanceof String &&
                            args[2] instanceof Number) {
                        builder.createAlias((String) args[0], (String) args[1], ((Number) args[2]).intValue())
                        return builder
                    }
                    return name
                case CriteriaMethods.IS_NULL:
                case CriteriaMethods.IS_NOT_NULL:
                case CriteriaMethods.IS_EMPTY:
                case CriteriaMethods.IS_NOT_EMPTY:
                    if (args.length == 1 && args[0] instanceof String) {
                        String value = (String) args[0]
                        switch (method) {
                            case CriteriaMethods.IS_NULL:
                                builder.getHibernateQuery().isNull(value)
                                break
                            case CriteriaMethods.IS_NOT_NULL:
                                builder.getHibernateQuery().isNotNull(value)
                                break
                            case CriteriaMethods.IS_EMPTY:
                                builder.getHibernateQuery().isEmpty(value)
                                break
                            case CriteriaMethods.IS_NOT_EMPTY:
                                builder.getHibernateQuery().isNotEmpty(value)
                                break
                            default:
                                break
                        }
                        return name
                    }
                    else if (args.length == 1 && args[0] != null) {
                        builder.throwRuntimeException(new IllegalArgumentException(
                                "call to [${name}] with value [${args[0]}] requires a String value."))
                    }
                    break
                default:
                    break
            }
        }
        return UNHANDLED
    }

    protected Object tryPropertyCriteria(CriteriaMethods method, Object... args) {
        if (method == CriteriaMethods.FETCH_MODE) {
            if (args.length == 2 && args[0] instanceof String && args[1] instanceof org.hibernate.FetchMode) {
                builder.fetchMode((String) args[0], (org.hibernate.FetchMode) args[1])
                return 'fetchMode'
            }
        }

        if (method == null || args.length < 2 || !(args[0] instanceof String)) {
            return UNHANDLED
        }
        String propertyName = (String) args[0]

        switch (method) {
            case CriteriaMethods.RLIKE:
                return builder.rlike(propertyName, args[1])
            case CriteriaMethods.BETWEEN:
                if (args.length >= 3) {
                    return builder.between(propertyName, args[1], args[2])
                }
                break
            case CriteriaMethods.EQUALS:
                if (args.length == 3 && args[2] instanceof Map) {
                    return builder.eq(propertyName, args[1], (Map) args[2])
                }
                return builder.eq(propertyName, args[1])
            case CriteriaMethods.EQUALS_PROPERTY:
                return builder.eqProperty(propertyName, args[1].toString())
            case CriteriaMethods.GREATER_THAN:
                return builder.gt(propertyName, args[1])
            case CriteriaMethods.GREATER_THAN_PROPERTY:
                return builder.gtProperty(propertyName, args[1].toString())
            case CriteriaMethods.GREATER_THAN_OR_EQUAL:
                return builder.ge(propertyName, args[1])
            case CriteriaMethods.GREATER_THAN_OR_EQUAL_PROPERTY:
                return builder.geProperty(propertyName, args[1].toString())
            case CriteriaMethods.ILIKE:
                return builder.ilike(propertyName, args[1])
            case CriteriaMethods.IN:
                if (args[1] instanceof Collection) {
                    return builder.in(propertyName, (Collection<?>) args[1])
                }
                else if (args[1] instanceof Object[]) {
                    return builder.in(propertyName, (Object[]) args[1])
                }
                break
            case CriteriaMethods.LESS_THAN:
                return builder.lt(propertyName, args[1])
            case CriteriaMethods.LESS_THAN_PROPERTY:
                return builder.ltProperty(propertyName, args[1].toString())
            case CriteriaMethods.LESS_THAN_OR_EQUAL:
                return builder.le(propertyName, args[1])
            case CriteriaMethods.LESS_THAN_OR_EQUAL_PROPERTY:
                return builder.leProperty(propertyName, args[1].toString())
            case CriteriaMethods.LIKE:
                return builder.like(propertyName, args[1])
            case CriteriaMethods.NOT_EQUAL:
                return builder.ne(propertyName, args[1])
            case CriteriaMethods.NOT_EQUAL_PROPERTY:
                return builder.neProperty(propertyName, args[1].toString())
            case CriteriaMethods.SIZE_EQUALS:
                if (args[1] instanceof Number) {
                    return builder.sizeEq(propertyName, ((Number) args[1]).intValue())
                }
                break
            default:
                break
        }
        return UNHANDLED
    }

    protected boolean isAssociationQueryMethod(Object... args) {
        return args.length == 1 && args[0] instanceof Closure
    }

    protected boolean isAssociationQueryWithJoinSpecificationMethod(Object... args) {
        return args.length == 2 && (args[0] instanceof Number) && (args[1] instanceof Closure)
    }

    protected boolean isCriteriaConstructionMethod(CriteriaMethods method, Object... args) {
        return (method == CriteriaMethods.LIST_CALL &&
                args.length == 2 &&
                args[0] instanceof Map &&
                args[1] instanceof Closure) ||
                (method == CriteriaMethods.ROOT_CALL ||
                        method == CriteriaMethods.ROOT_DO_CALL ||
                        method == CriteriaMethods.LIST_CALL ||
                        method == CriteriaMethods.LIST_DISTINCT_CALL ||
                        method == CriteriaMethods.GET_CALL ||
                        method == CriteriaMethods.COUNT_CALL ||
                        (method == CriteriaMethods.SCROLL_CALL && args.length == 1 && args[0] instanceof Closure))
    }

    protected void invokeClosureNode(Object args) {
        Closure<?> callable = (Closure<?>) args
        callable.setDelegate(builder)
        callable.setResolveStrategy(Closure.DELEGATE_FIRST)
        callable.call()
    }

}
