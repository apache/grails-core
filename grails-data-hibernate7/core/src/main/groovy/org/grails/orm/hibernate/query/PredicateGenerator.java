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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaInPredicate;
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

    public PredicateGenerator(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public Predicate[] getPredicates(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root_,
            List<? extends Query.QueryElement> criteriaList,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity) {

        List<Predicate> list = criteriaList.stream()
                .map(criterion -> handleCriterion(cb, criteriaQuery, root_, fromsByProvider, entity, criterion))
                .filter(Objects::nonNull)
                .toList();

        if (list.isEmpty()) {
            list = List.of(cb.equal(cb.literal(1), cb.literal(1)));
        }
        return list.toArray(new Predicate[0]);
    }

    private Predicate handleCriterion(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.QueryElement criterion) {
        if (criterion instanceof Query.Junction junction) {
            return handleJunction(cb, criteriaQuery, root, fromsByProvider, entity, junction);
        } else if (criterion instanceof Query.DistinctProjection) {
            return cb.conjunction();
        } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
            return handleAssociationCriteria(cb, criteriaQuery, fromsByProvider, c);
        } else if (criterion instanceof HibernateAssociationQuery haq) {
            return handleHibernateAssociationQuery(cb, criteriaQuery, fromsByProvider, haq);
        } else if (criterion instanceof Query.PropertyCriterion pc) {
            return handlePropertyCriterion(cb, criteriaQuery, root, fromsByProvider, entity, pc);
        } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
            return handlePropertyComparisonCriterion(cb, fromsByProvider, c);
        } else if (criterion instanceof Query.PropertyNameCriterion c) {
            return handlePropertyNameCriterion(cb, fromsByProvider, c);
        } else if (criterion instanceof Query.Exists c) {
            return handleExists(
                    cb, criteriaQuery, root, fromsByProvider, c.getSubquery().getPersistentEntity(), c);
        } else if (criterion instanceof Query.NotExists c) {
            PersistentEntity childEntity = c.getSubquery().getPersistentEntity();
            return cb.not(handleExists(
                    cb, criteriaQuery, root, fromsByProvider, childEntity, new Query.Exists(c.getSubquery())));
        } else if (criterion instanceof HibernateAlias) {
            return null; // Metadata only, handled by JpaFromProvider
        }
        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

    private Predicate handleJunction(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root_,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.Junction junction) {
        var predicates = getPredicates(cb, criteriaQuery, root_, junction.getCriteria(), fromsByProvider, entity);
        if (junction instanceof Query.Disjunction) {
            return cb.or(predicates);
        } else if (junction instanceof Query.Conjunction) {
            return cb.and(predicates);
        } else if (junction instanceof Query.Negation) {
            if (predicates.length != 1) {
                log.error("Must have a single predicate behind a not");
                throw new RuntimeException("Must have a single predicate behind a not");
            }
            return cb.not(predicates[0]);
        }
        return null;
    }

    private Predicate handleAssociationCriteria(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            DetachedAssociationCriteria<?> c) {

        From<?, ?> child = (From<?, ?>) fromsByProvider.getFullyQualifiedPath(c.getAssociationPath());
        PersistentEntity associatedEntity = c.getAssociation().getAssociatedEntity();

        JpaFromProvider childTablesByName = new JpaFromProvider(
                fromsByProvider,
                associatedEntity,
                c.getCriteria(),
                java.util.Collections.emptyList(),
                c.getFetchStrategies(),
                c.getJoinTypes(),
                child);

        return cb.and(getPredicates(cb, criteriaQuery, child, c.getCriteria(), childTablesByName, associatedEntity));
    }

    private Predicate handleHibernateAssociationQuery(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            HibernateAssociationQuery haq) {
        From<?, ?> child = (From<?, ?>) fromsByProvider.getFullyQualifiedPath(haq.associationPath);
        JpaFromProvider childFroms = new JpaFromProvider(
                fromsByProvider,
                haq.getEntity(),
                haq.getAssociationCriteria(),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                child);
        return cb.and(
                getPredicates(cb, criteriaQuery, child, haq.getAssociationCriteria(), childFroms, haq.getEntity()));
    }

    private Predicate handlePropertyCriterion(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.PropertyCriterion pc) {

        String propertyName = pc.getProperty();
        if (!"id".equals(propertyName) &&
                !propertyName.contains(".") &&
                entity.getPropertyByName(propertyName) == null &&
                !fromsByProvider.hasAlias(propertyName)) {
            throw new ConfigurationException(
                    "Property [" + propertyName + "] is not a valid property of class [" + entity.getName() + "]");
        }

        var fullyQualifiedPath = fromsByProvider.getFullyQualifiedPath(pc.getProperty());

        if (pc instanceof Query.NotIn c) {
            return handleNotIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
        } else if (pc instanceof Query.SubqueryCriterion c) {
            return handleSubqueryCriterion(cb, criteriaQuery, fromsByProvider, c);
        } else if (pc instanceof Query.In c) {
            return handleIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
        } else if (pc instanceof Query.ILike c) {
            return cb.ilike(
                    (Expression<String>) fullyQualifiedPath, c.getValue().toString());
        } else if (pc instanceof Query.RLike c) {
            return handleRLike(cb, fullyQualifiedPath, c);
        } else if (pc instanceof Query.Like c) {
            return cb.like((Expression<String>) fullyQualifiedPath, c.getValue().toString());
        } else if (pc instanceof Query.Equals c) {
            return cb.equal(fullyQualifiedPath, normalizeValue(c.getValue()));
        } else if (pc instanceof Query.NotEquals c) {
            return cb.or(cb.notEqual(fullyQualifiedPath, normalizeValue(c.getValue())), cb.isNull(fullyQualifiedPath));
        } else if (pc instanceof Query.IdEquals c) {
            return cb.equal(root.get("id"), normalizeValue(c.getValue()));
        } else if (pc instanceof Query.GreaterThan c) {
            Expression<? extends Number> rhs = resolveNumericExpression(cb, root, c);
            return rhs != null ? cb.gt((Expression<? extends Number>) fullyQualifiedPath, rhs) : cb.gt((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.GreaterThanEquals c) {
            Expression<? extends Number> rhs = resolveNumericExpression(cb, root, c);
            return rhs != null ? cb.ge((Expression<? extends Number>) fullyQualifiedPath, rhs) : cb.ge((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.LessThan c) {
            Expression<? extends Number> rhs = resolveNumericExpression(cb, root, c);
            return rhs != null ? cb.lt((Expression<? extends Number>) fullyQualifiedPath, rhs) : cb.lt((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.LessThanEquals c) {
            Expression<? extends Number> rhs = resolveNumericExpression(cb, root, c);
            return rhs != null ? cb.le((Expression<? extends Number>) fullyQualifiedPath, rhs) : cb.le((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.SizeEquals c) {
            return cb.equal(cb.size((Expression) fullyQualifiedPath), normalizeValue(c.getValue()));
        } else if (pc instanceof Query.SizeNotEquals c) {
            return cb.notEqual(cb.size((Expression) fullyQualifiedPath), normalizeValue(c.getValue()));
        } else if (pc instanceof Query.SizeGreaterThan c) {
            return cb.gt(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeGreaterThanEquals c) {
            return cb.ge(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeLessThan c) {
            return cb.lt(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeLessThanEquals c) {
            return cb.le(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.Between c) {
            return cb.between((Expression) fullyQualifiedPath, (Comparable) normalizeValue(c.getFrom()), (Comparable) normalizeValue(c.getTo()));
        }
        return null;
    }

    private Predicate handleNotIn(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.NotIn c,
            Path fullyQualifiedPath) {
        var queryableCriteria = getQueryableCriteriaFromInCriteria(c);
        if (Objects.nonNull(queryableCriteria)) {
            return cb.not(getQueryableCriteriaValue(cb, criteriaQuery, fromsByProvider, entity, c, queryableCriteria));
        } else if (Objects.nonNull(c.getSubquery()) &&
                !c.getSubquery().getProjections().isEmpty()) {
            Subquery subquery2 = criteriaQuery.subquery(Number.class);
            PersistentEntity subEntity = c.getValue().getPersistentEntity();
            Root from2 = subquery2.from(subEntity.getJavaClass());
            JpaFromProvider newMap2 = (JpaFromProvider) fromsByProvider.clone();
            var projection = c.getSubquery().getProjections().get(0);
            if (projection instanceof Query.PropertyProjection pp) {
                boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                Predicate[] predicates2 =
                        getPredicates(cb, criteriaQuery, from2, c.getValue().getCriteria(), newMap2, subEntity);
                subquery2
                        .select(from2.get(pp.getPropertyName()))
                        .distinct(distinct)
                        .where(cb.and(predicates2));
                return cb.not(cb.in(fullyQualifiedPath).value(subquery2));
            } else if (projection instanceof Query.IdProjection) {
                Predicate[] predicates2 =
                        getPredicates(cb, criteriaQuery, from2, c.getValue().getCriteria(), newMap2, subEntity);
                subquery2.select(from2).where(cb.and(predicates2));
                return cb.not(cb.in(fullyQualifiedPath).value(subquery2));
            }
        }
        return cb.not(cb.in(fullyQualifiedPath, c.getValue()));
    }

    private Predicate handleIn(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.In c,
            Path<?> fullyQualifiedPath) {
        var queryableCriteria = getQueryableCriteriaFromInCriteria(c);
        if (Objects.nonNull(queryableCriteria)) {
            return getQueryableCriteriaValue(cb, criteriaQuery, fromsByProvider, entity, c, queryableCriteria);
        } else if (!c.getValues().isEmpty()) {
            if (c.getValues().iterator().next() instanceof GormEntity firstEntity) {
                List<GormEntity> gormEntities = new ArrayList<>(c.getValues());
                Path id = criteriaQuery.from(firstEntity.getClass()).get("id");
                Collection newValues =
                        gormEntities.stream().map(GormEntity::ident).toList();
                return cb.in(id, newValues);
            }

            // Hibernate 7: If the path is a collection, we must ensure it's correctly handled
            if (fullyQualifiedPath instanceof SqmPath sqmPath &&
                    sqmPath.getReferencedPathSource() instanceof jakarta.persistence.metamodel.PluralAttribute) {
                // For basic collections, GORM's 'in' traditionally implies joining.
                // We'll check if the path is already a join (From)
                if (fullyQualifiedPath instanceof From) {
                    return cb.in(fullyQualifiedPath, c.getValues());
                }
                // If not joined yet, we may need to use 'elements' or MEMBER OF
                // but usually JpaFromProvider should have joined it if it was a property path
                // that refers to a collection.
            }

            return cb.in(fullyQualifiedPath, c.getValues());
        }
        return null;
    }

    private Predicate handleRLike(HibernateCriteriaBuilder cb, Path fullyQualifiedPath, Query.RLike c) {
        String pattern = c.getPattern().replaceAll("^/|/$", "");
        return cb.equal(
                cb.function(
                        GrailsRLikeFunctionContributor.RLIKE, Boolean.class, fullyQualifiedPath, cb.literal(pattern)),
                true);
    }

    private Predicate handleSubqueryCriterion(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            Query.SubqueryCriterion c) {
        Subquery subquery = criteriaQuery.subquery(Number.class);
        PersistentEntity subEntity = c.getValue().getPersistentEntity();
        Root from = subquery.from(subEntity.getJavaClass());
        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
        newMap.put("root", from);
        Predicate[] predicates =
                getPredicates(cb, criteriaQuery, from, c.getValue().getCriteria(), newMap, subEntity);
        Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());

        if (c instanceof Query.GreaterThanEqualsAll) {
            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.and(predicates));
            return cb.greaterThanOrEqualTo(path, subquery);
        } else if (c instanceof Query.GreaterThanAll) {
            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.and(predicates));
            return cb.greaterThan(path, subquery);
        } else if (c instanceof Query.LessThanEqualsAll) {
            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.and(predicates));
            return cb.lessThanOrEqualTo(path, subquery);
        } else if (c instanceof Query.LessThanAll) {
            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.and(predicates));
            return cb.lessThan(path, subquery);
        } else if (c instanceof Query.EqualsAll) {
            subquery.select(from.get(c.getProperty())).where(cb.and(predicates));
            return cb.equal(path, subquery);
        } else if (c instanceof Query.GreaterThanEqualsSome) {
            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.or(predicates));
            return cb.greaterThanOrEqualTo(path, subquery);
        } else if (c instanceof Query.GreaterThanSome) {
            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.or(predicates));
            return cb.greaterThan(path, subquery);
        } else if (c instanceof Query.LessThanEqualsSome) {
            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.or(predicates));
            return cb.lessThanOrEqualTo(path, subquery);
        } else if (c instanceof Query.LessThanSome) {
            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.or(predicates));
            return cb.lessThan(path, subquery);
        }
        return null;
    }

    private Predicate handlePropertyComparisonCriterion(
            HibernateCriteriaBuilder cb, JpaFromProvider fromsByProvider, Query.PropertyComparisonCriterion c) {
        Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());
        Path otherPath = fromsByProvider.getFullyQualifiedPath(c.getOtherProperty());
        if (!path.getJavaType().equals(otherPath.getJavaType())) {
            jakarta.persistence.criteria.Path parentOfPath = path.getParentPath();
            if (parentOfPath != null && parentOfPath.getJavaType().equals(otherPath.getJavaType())) {
                path = parentOfPath;
            } else {
                jakarta.persistence.criteria.Path parentOfOther = otherPath.getParentPath();
                if (parentOfOther != null && parentOfOther.getJavaType().equals(path.getJavaType())) {
                    otherPath = parentOfOther;
                }
            }
        }
        if (c instanceof Query.EqualsProperty) return cb.equal(path, otherPath);
        if (c instanceof Query.NotEqualsProperty) return cb.notEqual(path, otherPath);
        if (c instanceof Query.LessThanEqualsProperty) return cb.le(path, otherPath);
        if (c instanceof Query.LessThanProperty) return cb.lt(path, otherPath);
        if (c instanceof Query.GreaterThanEqualsProperty) return cb.ge(path, otherPath);
        if (c instanceof Query.GreaterThanProperty) return cb.gt(path, otherPath);
        return null;
    }

    private Predicate handlePropertyNameCriterion(
            HibernateCriteriaBuilder cb, JpaFromProvider fromsByProvider, Query.PropertyNameCriterion c) {
        Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());
        if (c instanceof Query.IsNull) return cb.isNull(path);
        if (c instanceof Query.IsNotNull) return cb.isNotNull(path);
        if (c instanceof Query.IsEmpty) return cb.isEmpty(path);
        if (c instanceof Query.IsNotEmpty) return cb.isNotEmpty(path);
        return null;
    }

    private Predicate handleExists(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root_,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.Exists c) {
        QueryableCriteria<?> subqueryable = c.getSubquery();
        PersistentEntity subEntity = subqueryable.getPersistentEntity();
        Subquery<Integer> subquery = criteriaQuery.subquery(Integer.class);
        Root<?> subRoot = subquery.from(subEntity.getJavaClass());

        JpaFromProvider subFromsProvider = new JpaFromProvider(
                fromsByProvider,
                subEntity,
                subqueryable.getCriteria(),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                subRoot);

        var predicates =
                getPredicates(cb, criteriaQuery, subRoot, subqueryable.getCriteria(), subFromsProvider, subEntity);
        var existsPredicate = getExistsPredicate(cb, root_, entity, subRoot);
        Predicate[] allPredicates = existsPredicate != null ?
                Stream.concat(Arrays.stream(predicates), Stream.of(existsPredicate))
                        .toArray(Predicate[]::new) :
                predicates;
        subquery.select(cb.literal(1)).where(cb.and(allPredicates));
        return cb.exists(subquery);
    }

    private CriteriaBuilder.In getQueryableCriteriaValue(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.PropertyNameCriterion criterion,
            QueryableCriteria queryableCriteria) {
        var projection = findPropertyOrIdProjection(queryableCriteria);
        var subProperty = findSubproperty(projection);
        var path = fromsByProvider.getFullyQualifiedPath(criterion.getProperty());
        boolean isAssociation = isAssociation(entity, criterion.getProperty());
        var in = findInPredicate(cb, projection, path, subProperty, isAssociation);

        PersistentEntity subEntity = queryableCriteria.getPersistentEntity();
        Class<?> subqueryType = subEntity.getJavaClass();
        if (projection instanceof Query.PropertyProjection propertyProjection) {
            PersistentProperty prop = subEntity.getPropertyByName(propertyProjection.getPropertyName());
            if (prop != null) {
                subqueryType = prop.getType();
            } else if (propertyProjection.getPropertyName().contains(".")) {
                // Handle aliased or nested properties in projections (e.g., "e1.id")
                String propName = propertyProjection.getPropertyName();
                String simplePropName = propName.substring(propName.lastIndexOf('.') + 1);
                PersistentProperty simpleProp = subEntity.getPropertyByName(simplePropName);
                if (simpleProp != null) {
                    subqueryType = simpleProp.getType();
                } else if ("id".equals(simplePropName)) {
                    subqueryType = subEntity.getIdentity() != null ?
                            subEntity.getIdentity().getType() :
                            Long.class;
                }
            }
        } else if (projection instanceof Query.IdProjection) {
            subqueryType =
                    subEntity.getIdentity() != null ? subEntity.getIdentity().getType() : Long.class;
        } else if (isAssociation) {
            subqueryType =
                    subEntity.getIdentity() != null ? subEntity.getIdentity().getType() : Long.class;
        }

        var subquery = criteriaQuery.subquery(subqueryType);
        var from = subquery.from(subEntity.getJavaClass());
        var clonedProviderByName = new JpaFromProvider(
                fromsByProvider, (DetachedCriteria<?>) queryableCriteria, java.util.Collections.emptyList(), from);
        var predicates = getPredicates(
                cb, criteriaQuery, from, queryableCriteria.getCriteria(), clonedProviderByName, subEntity);
        subquery.select((Expression) clonedProviderByName.getFullyQualifiedPath(subProperty))
                .distinct(true)
                .where(cb.and(predicates));
        return in.value(subquery);
    }

    private boolean isAssociation(PersistentEntity entity, String propertyName) {
        if ("id".equals(propertyName) ||
                (entity.getIdentity() != null &&
                        propertyName.equals(entity.getIdentity().getName()))) {
            return false;
        }
        PersistentProperty prop = entity.getPropertyByName(propertyName);
        return prop instanceof Association;
    }

    private Predicate getExistsPredicate(
            HibernateCriteriaBuilder cb, From<?, ?> root_, PersistentEntity childPersistentEntity, Root subRoot) {
        return childPersistentEntity.getAssociations().stream()
                .filter(assoc -> assoc.getAssociatedEntity().getJavaClass().equals(root_.getJavaType()))
                .findFirst()
                .map(owner -> (Predicate) cb.equal(subRoot.get(owner.getName()), root_))
                .orElse(null);
    }

    private JpaInPredicate findInPredicate(
            HibernateCriteriaBuilder cb, Object projection, Path path, String subProperty, boolean isAssociation) {
        if (projection instanceof Query.PropertyProjection || !isAssociation) {
            return cb.in(path);
        } else {
            return cb.in(((SqmPath) path).get(subProperty));
        }
    }

    private String findSubproperty(Object projection) {
        return projection instanceof Query.PropertyProjection ?
                ((Query.PropertyProjection) projection).getPropertyName() :
                "id";
    }

    private Query.Projection findPropertyOrIdProjection(QueryableCriteria queryableCriteria) {
        return (Query.Projection) queryableCriteria.getProjections().stream()
                .filter(p -> p instanceof Query.PropertyProjection || p instanceof Query.IdProjection)
                .findFirst()
                .orElse(new Query.IdProjection());
    }

    private QueryableCriteria getQueryableCriteriaFromInCriteria(Query.Criterion criterion) {
        return criterion instanceof Query.In ?
                ((Query.In) criterion).getSubquery() :
                ((Query.NotIn) criterion).getSubquery();
    }

    /**
     * Normalizes a criterion value for use with JPA Criteria API.
     * Hibernate 7's SqmCriteriaNodeBuilder requires strict Java types and cannot
     * coerce Groovy types like GString. This method converts CharSequence (including
     * GString) to String so Hibernate can process the value correctly.
     */
    private static Object normalizeValue(Object value) {
        if (value instanceof CharSequence && !(value instanceof String)) {
            return value.toString();
        }
        return value;
    }

    private Number getNumericValue(Query.PropertyCriterion criterion) {
        Object value = criterion.getValue();
        if (value != null) {
            try {
                return conversionService.convert(value, Number.class);
            } catch (org.springframework.core.convert.ConversionException e) {
                throw new ConfigurationException(
                        String.format(
                                "Operation '%s' on property '%s' only accepts a numeric value, but received a %s",
                                criterion.getClass().getSimpleName(),
                                criterion.getProperty(),
                                value.getClass().getName()),
                        e);
            }
        }
        throw new ConfigurationException(String.format(
                "Operation '%s' on property '%s' only accepts a numeric value, but received a %s",
                criterion.getClass().getSimpleName(), criterion.getProperty(), "null"));
    }

    @SuppressWarnings("unchecked")
    private Expression<? extends Number> resolveNumericExpression(HibernateCriteriaBuilder cb, From<?, ?> root, Query.PropertyCriterion criterion) {
        Object value = criterion.getValue();
        if (!(value instanceof PropertyArithmetic)) {
            return null;
        }
        PropertyArithmetic pa = (PropertyArithmetic) value;
        Expression<Number> propertyPath = root.get(pa.propertyName());
        return switch (pa.operator()) {
            case MULTIPLY -> cb.prod(propertyPath, pa.operand());
            case ADD      -> cb.sum(propertyPath, pa.operand());
            case SUBTRACT -> cb.diff(propertyPath, pa.operand());
            case DIVIDE   -> cb.quot(propertyPath, pa.operand());
        };
    }
}
