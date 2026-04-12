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
package org.grails.orm.hibernate.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import groovy.util.logging.Slf4j;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.criteria.JpaSubQuery;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;

@Slf4j
@SuppressWarnings({
    "PMD.DataflowAnomalyAnalysis",
    "PMD.AvoidLiteralsInIfCondition",
    "PMD.AvoidDuplicateLiterals",
    "unchecked",
    "rawtypes"
})
public class PredicateGenerator {

    private static final Logger log = LoggerFactory.getLogger(PredicateGenerator.class);

    private final ConversionService conversionService;
    private final HibernateCriteriaBuilder criteriaBuilder;

    public PredicateGenerator(HibernateCriteriaBuilder criteriaBuilder, ConversionService conversionService) {
        this.criteriaBuilder = criteriaBuilder;
        this.conversionService = conversionService;
    }

    public Predicate[] getPredicates(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root_,
            List<? extends Query.QueryElement> criteriaList,
            JpaQueryContext fromsByProvider,
            PersistentEntity entity) {

        List<Predicate> list = criteriaList.stream()
                .map(criterion -> handleCriterion(criteriaQuery, root_, fromsByProvider, entity, criterion))
                .filter(Objects::nonNull)
                .toList();

        if (list.isEmpty()) {
            list = List.of(criteriaBuilder.equal(criteriaBuilder.literal(1), criteriaBuilder.literal(1)));
        }
        return list.toArray(new Predicate[0]);
    }

    private Predicate handleCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            PersistentEntity entity,
            Query.QueryElement criterion) {

        if (criterion instanceof Query.Junction junction) {
            return handleJunction(criteriaQuery, root, fromsByProvider, entity, junction);
        } else if (criterion instanceof Query.DistinctProjection) {
            return criteriaBuilder.conjunction();
        } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
            return handleAssociationCriteria(criteriaQuery, fromsByProvider, c);
        } else if (criterion instanceof HibernateAssociationQuery haq) {
            return handleHibernateAssociationQuery(criteriaQuery, fromsByProvider, haq);
        } else if (criterion instanceof Query.PropertyCriterion pc) {
            return handlePropertyCriterion(criteriaQuery, root, fromsByProvider, entity, pc);
        } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
            return handlePropertyComparisonCriterion(fromsByProvider, c);
        } else if (criterion instanceof Query.PropertyNameCriterion c) {
            return handlePropertyNameCriterion(fromsByProvider, c);
        } else if (criterion instanceof Query.Exists c) {
            return handleExists(
                    criteriaQuery, root, fromsByProvider, c.getSubquery().getPersistentEntity(), c);
        } else if (criterion instanceof Query.NotExists c) {
            PersistentEntity childEntity = c.getSubquery().getPersistentEntity();
            return criteriaBuilder.not(handleExists(
                    criteriaQuery, root, fromsByProvider, childEntity, new Query.Exists(c.getSubquery())));
        } else if (criterion instanceof HibernateAlias) {
            return null; // Metadata only, handled by JpaQueryContext
        }
        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

    private Predicate handleJunction(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root_,
            JpaQueryContext fromsByProvider,
            PersistentEntity entity,
            Query.Junction junction) {
        var predicates = getPredicates(criteriaQuery, root_, junction.getCriteria(), fromsByProvider, entity);
        if (junction instanceof Query.Disjunction) {
            return criteriaBuilder.or(predicates);
        } else if (junction instanceof Query.Conjunction) {
            return criteriaBuilder.and(predicates);
        } else if (junction instanceof Query.Negation) {
            if (predicates.length != 1) {
                log.error("Must have a single predicate behind a not");
                throw new RuntimeException("Must have a single predicate behind a not");
            }
            return criteriaBuilder.not(predicates[0]);
        }
        return null;
    }

    private Predicate handleAssociationCriteria(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            DetachedAssociationCriteria<?> c) {

        From<?, ?> child = (From<?, ?>) fromsByProvider.getFullyQualifiedPath(c.getAssociationPath());
        PersistentEntity associatedEntity = c.getAssociation().getAssociatedEntity();

        JpaQueryContext childTablesByName = new JpaQueryContext(
                fromsByProvider,
                associatedEntity,
                c.getCriteria(),
                java.util.Collections.emptyList(),
                c.getFetchStrategies(),
                c.getJoinTypes(),
                child);

        return criteriaBuilder.and(getPredicates(criteriaQuery, child, c.getCriteria(), childTablesByName, associatedEntity));
    }

    private Predicate handleHibernateAssociationQuery(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            HibernateAssociationQuery haq) {
        From<?, ?> child = (From<?, ?>) fromsByProvider.getFullyQualifiedPath(haq.associationPath);
        JpaQueryContext childFroms = new JpaQueryContext(
                fromsByProvider,
                haq.getEntity(),
                haq.getAssociationCriteria(),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                child);
        return criteriaBuilder.and(
                getPredicates(criteriaQuery, child, haq.getAssociationCriteria(), childFroms, haq.getEntity()));
    }

    private Predicate handlePropertyCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            PersistentEntity entity,
            Query.PropertyCriterion pc) {

        String propertyName = pc.getProperty();
        if (!"id".equals(propertyName) &&
                !propertyName.contains(".") &&
                entity.getPropertyByName(propertyName) == null &&
                !fromsByProvider.hasAlias(propertyName)) {
            throw new ConfigurationException("Property [" + propertyName + "] is not a valid property of [" + entity.getName() + "]");
        }

        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName);

        if (pc instanceof Query.Equals) {
            return handleEquals(criteriaQuery, pc, propertyPath);
        } else if (pc instanceof Query.NotEquals) {
            return criteriaBuilder.not(handleEquals(criteriaQuery, pc, propertyPath));
        } else if (pc instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.getValue()));
        } else if (pc instanceof Query.ILike) {
            return criteriaBuilder.like(criteriaBuilder.lower((Expression<String>) propertyPath), ((String) convertValue(entity, propertyName, pc.getValue())).toLowerCase());
        } else if (pc instanceof Query.RLike rLike) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.getValue()));
        } else if (pc instanceof Query.GreaterThan) {
            Object value = convertValue(entity, propertyName, pc.getValue());
            if (value instanceof Number) {
                return criteriaBuilder.gt((Expression<? extends Number>) propertyPath, (Number) value);
            } else {
                return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Comparable) value);
            }
        } else if (pc instanceof Query.GreaterThanEquals) {
            Object value = convertValue(entity, propertyName, pc.getValue());
            if (value instanceof Number) {
                return criteriaBuilder.ge((Expression<? extends Number>) propertyPath, (Number) value);
            } else {
                return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) value);
            }
        } else if (pc instanceof Query.LessThan) {
            Object value = convertValue(entity, propertyName, pc.getValue());
            if (value instanceof Number) {
                return criteriaBuilder.lt((Expression<? extends Number>) propertyPath, (Number) value);
            } else {
                return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Comparable) value);
            }
        } else if (pc instanceof Query.LessThanEquals) {
            Object value = convertValue(entity, propertyName, pc.getValue());
            if (value instanceof Number) {
                return criteriaBuilder.le((Expression<? extends Number>) propertyPath, (Number) value);
            } else {
                return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) value);
            }
        } else if (pc instanceof Query.In) {
            Collection<?> values = (Collection<?>) pc.getValue();
            var in = criteriaBuilder.in(propertyPath);
            for (Object value : values) {
                ((CriteriaBuilder.In)in).value(convertValue(entity, propertyName, value));
            }
            return in;
        } else if (pc instanceof Query.Between between) {
            return criteriaBuilder.between((Expression<? extends Comparable>) propertyPath, (Comparable) convertValue(entity, propertyName, between.getFrom()), (Comparable) convertValue(entity, propertyName, between.getTo()));
        } else if (((Object)pc) instanceof Query.IsNull) {
            return criteriaBuilder.isNull(propertyPath);
        } else if (((Object)pc) instanceof Query.IsNotNull) {
            return criteriaBuilder.isNotNull(propertyPath);
        } else if (((Object)pc) instanceof Query.IsEmpty) {
            return criteriaBuilder.isEmpty((Expression<Collection>) propertyPath);
        } else if (((Object)pc) instanceof Query.IsNotEmpty) {
            return criteriaBuilder.isNotEmpty((Expression<Collection>) propertyPath);
        } else if (pc instanceof Query.SizeEquals sizeEquals) {
            return criteriaBuilder.equal(criteriaBuilder.size((Expression<Collection>) propertyPath), (Integer) sizeEquals.getValue());
        } else if (pc instanceof Query.SizeNotEquals sizeNotEquals) {
            return criteriaBuilder.notEqual(criteriaBuilder.size((Expression<Collection>) propertyPath), (Integer) sizeNotEquals.getValue());
        } else if (pc instanceof Query.SizeGreaterThan sizeGreaterThan) {
            return criteriaBuilder.gt(criteriaBuilder.size((Expression<Collection>) propertyPath), (Integer) sizeGreaterThan.getValue());
        } else if (pc instanceof Query.SizeGreaterThanEquals sizeGreaterThanEquals) {
            return criteriaBuilder.ge(criteriaBuilder.size((Expression<Collection>) propertyPath), (Integer) sizeGreaterThanEquals.getValue());
        } else if (pc instanceof Query.SizeLessThan sizeLessThan) {
            return criteriaBuilder.lt(criteriaBuilder.size((Expression<Collection>) propertyPath), (Integer) sizeLessThan.getValue());
        } else if (pc instanceof Query.SizeLessThanEquals sizeLessThanEquals) {
            return criteriaBuilder.le(criteriaBuilder.size((Expression<Collection>) propertyPath), (Integer) sizeLessThanEquals.getValue());
        }

        throw new UnsupportedOperationException("Unsupported criterion: " + pc.getClass().getName());
    }

    private Predicate handlePropertyComparisonCriterion(JpaQueryContext fromsByProvider, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        Expression<?> otherPropertyPath = fromsByProvider.getFullyQualifiedExpression(c.getOtherProperty());

        if (c instanceof Query.EqualsProperty) {
            return criteriaBuilder.equal(propertyPath, otherPropertyPath);
        } else if (c instanceof Query.NotEqualsProperty) {
            return criteriaBuilder.notEqual(propertyPath, otherPropertyPath);
        } else if (c instanceof Query.GreaterThanProperty) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        } else if (c instanceof Query.GreaterThanEqualsProperty) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        } else if (c instanceof Query.LessThanProperty) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        } else if (c instanceof Query.LessThanEqualsProperty) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath);
        }

        throw new UnsupportedOperationException("Unsupported property comparison criterion: " + c.getClass().getName());
    }

    private Predicate handlePropertyNameCriterion(JpaQueryContext fromsByProvider, Query.PropertyNameCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        if (((Object)c) instanceof Query.IsNull) {
            return criteriaBuilder.isNull(propertyPath);
        } else if (((Object)c) instanceof Query.IsNotNull) {
            return criteriaBuilder.isNotNull(propertyPath);
        } else if (((Object)c) instanceof Query.IsEmpty) {
            return criteriaBuilder.isEmpty((Expression<Collection>) propertyPath);
        } else if (((Object)c) instanceof Query.IsNotEmpty) {
            return criteriaBuilder.isNotEmpty((Expression<Collection>) propertyPath);
        }
        throw new UnsupportedOperationException("Unsupported property name criterion: " + c.getClass().getName());
    }

    private Predicate handleEquals(AbstractQuery<?> criteriaQuery, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        if (pc.getValue() instanceof QueryableCriteria qc) {
            Subquery<?> subquery = criteriaQuery.subquery(qc.getPersistentEntity().getJavaClass());
            new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService).populateSubquery((JpaSubQuery)subquery);
            return criteriaBuilder.equal(propertyPath, subquery);
        } else {
            return criteriaBuilder.equal(propertyPath, convertValue(null, pc.getProperty(), pc.getValue()));
        }
    }

    private Predicate handleExists(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            PersistentEntity entity,
            Query.Exists exists) {
        QueryableCriteria subqueryCriteria = exists.getSubquery();
        Subquery<?> subquery = criteriaQuery.subquery(entity.getJavaClass());
        new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, (DetachedCriteria) subqueryCriteria, conversionService).populateSubquery((JpaSubQuery)subquery);
        return criteriaBuilder.exists(subquery);
    }

    private Object convertValue(PersistentEntity entity, String propertyName, Object value) {
        if (value == null) {
            return null;
        }
        if (conversionService.canConvert(value.getClass(), Object.class)) {
            return conversionService.convert(value, Object.class);
        }
        return value;
    }

    public Predicate generate(List<Query.Criterion> criteriaList, AbstractQuery<?> cq, From<?, ?> root, JpaQueryContext tablesByName, PersistentEntity entity) {
        Predicate[] predicates = getPredicates(cq, root, criteriaList, tablesByName, entity);
        return criteriaBuilder.and(predicates);
    }
}
