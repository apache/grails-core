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

import grails.gorm.DetachedCriteria;
import grails.gorm.PagedResultList;
import groovy.lang.Closure;
import groovy.lang.MetaMethod;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.Map;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.grails.orm.hibernate.query.HibernateQueryConstants;
import org.springframework.beans.BeanUtils;

public class CriteriaMethodInvoker {

  private static final Object UNHANDLED = new Object();

  private final HibernateCriteriaBuilder builder;

  public CriteriaMethodInvoker(HibernateCriteriaBuilder builder) {
    this.builder = builder;
  }

  public Object invokeMethod(String name, Object[] args) {
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

  private Object tryCriteriaConstruction(CriteriaMethods method, Object[] args) {
    if (method == null || !isCriteriaConstructionMethod(method, args)) {
      return UNHANDLED;
    }

    HibernateQuery hibernateQuery = builder.getHibernateQuery();
    switch (method) {
      case GET_CALL -> builder.setUniqueResult(true);
      case SCROLL_CALL -> builder.setScroll(true);
      case COUNT_CALL -> builder.setCount(true);
      case LIST_DISTINCT_CALL -> builder.setDistinct(true);
    }

    // Check for pagination params
    if (method == CriteriaMethods.LIST_CALL && args.length == 2) {
      builder.setPaginationEnabledList(true);
      if (args[0] instanceof Map map) {
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
        Map argMap = (Map) args[0];
        final String sortField = (String) argMap.get(HibernateQueryConstants.ARGUMENT_SORT);
        if (sortField != null) {
          final boolean ignoreCase =
              !(argMap.get(HibernateQueryConstants.ARGUMENT_IGNORE_CASE) instanceof Boolean b) || b;
          final String orderParam = (String) argMap.get(HibernateQueryConstants.ARGUMENT_ORDER);
          final Query.Order.Direction direction =
              Query.Order.Direction.DESC.name().equalsIgnoreCase(orderParam)
                  ? Query.Order.Direction.DESC
                  : Query.Order.Direction.ASC;
          Query.Order order;
          if (ignoreCase) {
            order = new Query.Order(sortField, direction);
            order.ignoreCase();
          } else {
            order = new Query.Order(sortField, direction);
          }
          hibernateQuery.order(order);
        }
        result = new PagedResultList<>(hibernateQuery);
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

  private Object tryMetaMethod(String name, Object[] args) {
    MetaMethod metaMethod = builder.getMetaClass().getMetaMethod(name, args);
    if (metaMethod != null) {
      return metaMethod.invoke(builder, args);
    }
    return UNHANDLED;
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  private Object tryAssociationOrJunction(String name, CriteriaMethods method, Object[] args) {
    if (!isAssociationQueryMethod(args) && !isAssociationQueryWithJoinSpecificationMethod(args)) {
      return UNHANDLED;
    }

    final boolean hasMoreThanOneArg = args.length > 1;
    final Closure callable = hasMoreThanOneArg ? (Closure) args[1] : (Closure) args[0];
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
      }
    }

    final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(builder.getTargetClass(), name);
    if (pd != null && pd.getReadMethod() != null) {
      final Metamodel metamodel = builder.getSessionFactory().getMetamodel();
      final EntityType<?> entityType = metamodel.entity(builder.getTargetClass());
      final Attribute<?, ?> attribute = entityType.getAttribute(name);

      if (attribute.isAssociation()) {
        Class oldTargetClass = builder.getTargetClass();
        builder.setTargetClass(builder.getClassForAssociationType(attribute));
        JoinType joinType;
        if (hasMoreThanOneArg) {
          joinType = builder.convertFromInt((Integer) args[0]);
        } else if (builder.getTargetClass().equals(oldTargetClass)) {
          joinType = JoinType.LEFT; // default to left join if joining on the same table
        } else {
          joinType = builder.convertFromInt(0);
        }

        hibernateQuery.join(name, joinType);
        hibernateQuery.in(name, new DetachedCriteria(builder.getTargetClass()).build(callable));
        builder.setTargetClass(oldTargetClass);

        return name;
      }
    }
    return UNHANDLED;
  }

      @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
      protected Object trySimpleCriteria(String name, CriteriaMethods method, Object[] args) {    if (args.length != 1 || args[0] == null) {
      return UNHANDLED;
    }

    if (method != null) {
      switch (method) {
        case ID_EQUALS:
          return builder.eq("id", args[0]);
        case IS_NULL, IS_NOT_NULL, IS_EMPTY, IS_NOT_EMPTY:
          if (!(args[0] instanceof String)) {
            builder.throwRuntimeException(
                new IllegalArgumentException(
                    "call to [" + name + "] with value [" + args[0] + "] requires a String value."));
          }
          final String value = (String) args[0];
          switch (method) {
            case IS_NULL -> builder.getHibernateQuery().isNull(builder.calculatePropertyName(value));
            case IS_NOT_NULL -> builder.getHibernateQuery().isNotNull(builder.calculatePropertyName(value));
            case IS_EMPTY -> builder.getHibernateQuery().isEmpty(builder.calculatePropertyName(value));
            case IS_NOT_EMPTY -> builder.getHibernateQuery().isNotEmpty(builder.calculatePropertyName(value));
          }
          return name;
      }
    }
    return UNHANDLED;
  }

  @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
  protected Object tryPropertyCriteria(CriteriaMethods method, Object[] args) {
    if (method == null || args.length < 2 || !(args[0] instanceof String propertyName)) {
      return UNHANDLED;
    }

    switch (method) {
      case RLIKE:
        return builder.rlike(builder.calculatePropertyName(propertyName), args[1]);
      case BETWEEN:
        if (args.length >= 3) {
          return builder.between(builder.calculatePropertyName(propertyName), args[1], args[2]);
        }
        break;
      case EQUALS:
        if (args.length == 3 && args[2] instanceof Map) {
          return builder.eq(builder.calculatePropertyName(propertyName), args[1], (Map) args[2]);
        }
        return builder.eq(builder.calculatePropertyName(propertyName), args[1]);
      case EQUALS_PROPERTY:
        return builder.eqProperty(builder.calculatePropertyName(propertyName), args[1].toString());
      case GREATER_THAN:
        return builder.gt(builder.calculatePropertyName(propertyName), args[1]);
      case GREATER_THAN_PROPERTY:
        return builder.gtProperty(builder.calculatePropertyName(propertyName), args[1].toString());
      case GREATER_THAN_OR_EQUAL:
        return builder.ge(builder.calculatePropertyName(propertyName), args[1]);
      case GREATER_THAN_OR_EQUAL_PROPERTY:
        return builder.geProperty(builder.calculatePropertyName(propertyName), args[1].toString());
      case ILIKE:
        return builder.ilike(builder.calculatePropertyName(propertyName), args[1]);
      case IN:
        if (args[1] instanceof Collection) {
          return builder.in(builder.calculatePropertyName(propertyName), (Collection) args[1]);
        } else if (args[1] instanceof Object[]) {
          return builder.in(builder.calculatePropertyName(propertyName), (Object[]) args[1]);
        }
        break;
      case LESS_THAN:
        return builder.lt(builder.calculatePropertyName(propertyName), args[1]);
      case LESS_THAN_PROPERTY:
        return builder.ltProperty(builder.calculatePropertyName(propertyName), args[1].toString());
      case LESS_THAN_OR_EQUAL:
        return builder.le(builder.calculatePropertyName(propertyName), args[1]);
      case LESS_THAN_OR_EQUAL_PROPERTY:
        return builder.leProperty(builder.calculatePropertyName(propertyName), args[1].toString());
      case LIKE:
        return builder.like(builder.calculatePropertyName(propertyName), args[1]);
      case NOT_EQUAL:
        return builder.ne(builder.calculatePropertyName(propertyName), args[1]);
      case NOT_EQUAL_PROPERTY:
        return builder.neProperty(builder.calculatePropertyName(propertyName), args[1].toString());
      case SIZE_EQUALS:
        if (args[1] instanceof Number) {
          return builder.sizeEq(
              builder.calculatePropertyName(propertyName), ((Number) args[1]).intValue());
        }
        break;
    }
    return UNHANDLED;
  }

  private boolean isAssociationQueryMethod(Object[] args) {
    return args.length == 1 && args[0] instanceof Closure;
  }

  private boolean isAssociationQueryWithJoinSpecificationMethod(Object[] args) {
    return args.length == 2 && (args[0] instanceof Number) && (args[1] instanceof Closure);
  }

  private boolean isCriteriaConstructionMethod(CriteriaMethods method, Object[] args) {
    return (method == CriteriaMethods.LIST_CALL
            && args.length == 2
            && args[0] instanceof Map
            && args[1] instanceof Closure)
        || (method == CriteriaMethods.ROOT_CALL
            || method == CriteriaMethods.ROOT_DO_CALL
            || method == CriteriaMethods.LIST_CALL
            || method == CriteriaMethods.LIST_DISTINCT_CALL
            || method == CriteriaMethods.GET_CALL
            || method == CriteriaMethods.COUNT_CALL
            || (method == CriteriaMethods.SCROLL_CALL
                && args.length == 1
                && args[0] instanceof Closure));
  }

  private void invokeClosureNode(Object args) {
    Closure<?> callable = (Closure<?>) args;
    callable.setDelegate(builder);
    callable.setResolveStrategy(Closure.DELEGATE_FIRST);
    callable.call();
  }
}
