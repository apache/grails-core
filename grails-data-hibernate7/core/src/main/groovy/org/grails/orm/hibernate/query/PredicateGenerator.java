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

import grails.gorm.DetachedCriteria;
import jakarta.persistence.criteria.*;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.Projections;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaSubQuery;
import org.springframework.core.convert.ConversionService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A class that generates predicates for a given list of criteria.
 *
 * @author walterduquedeestrada
 * @author graemerocher
 * @since 7.0.0
 */
public class PredicateGenerator {

    private final HibernateCriteriaBuilder criteriaBuilder;
    private final ConversionService conversionService;

    public PredicateGenerator(HibernateCriteriaBuilder criteriaBuilder, ConversionService conversionService) {
        this.criteriaBuilder = criteriaBuilder;
        this.conversionService = conversionService;
    }

    public Predicate[] getPredicates(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            List<? extends Query.QueryElement> criteria,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity) {
        return criteria.stream()
                .map(c -> handleCriterion(criteriaQuery, root, fromsByProvider, entity, c))
                .filter(Objects::nonNull)
                .toList()
                .toArray(new Predicate[0]);
    }

    private boolean isCollectionPath(Expression<?> expression) {
        if (expression instanceof jakarta.persistence.criteria.Path<?> path) {
            return Collection.class.isAssignableFrom(path.getJavaType());
        }
        return false;
    }

    public Predicate handleCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.QueryElement criterion) {

        if (criterion instanceof Query.Junction junction) {
            return handleJunction(criteriaQuery, root, fromsByProvider, entity, junction);
        } else if (criterion instanceof Query.DistinctProjection) {
            return criteriaBuilder.conjunction();
        } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
            return handleAssociationCriteria(criteriaQuery, fromsByProvider, c);
        } else if (criterion instanceof HibernateAssociationQuery haq) {
            return handleHibernateAssociationQuery(criteriaQuery, fromsByProvider, haq);
        } else if (criterion instanceof Query.SubqueryCriterion c) {
            return handleSubqueryCriterion(criteriaQuery, root, fromsByProvider, entity, c);
        } else if (criterion instanceof Query.IdEquals idEquals) {
            String propertyName = entity.getIdentity().getName();
            Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName);
            return criteriaBuilder.equal(propertyPath, convertValue(entity, propertyName, idEquals.getValue(), propertyPath));
        } else if (criterion instanceof Query.PropertyCriterion pc) {
            return handlePropertyCriterion(criteriaQuery, root, fromsByProvider, entity, pc);
        } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
            return handlePropertyComparisonCriterion(fromsByProvider, c);
        } else if (criterion instanceof Query.PropertyNameCriterion c) {
            return handlePropertyNameCriterion(fromsByProvider, c);
        } else if (criterion instanceof Query.Exists c) {
            return handleExists(
                    criteriaQuery, fromsByProvider, c);
        } else if (criterion instanceof Query.NotExists c) {
            return criteriaBuilder.not(handleExists(
                    criteriaQuery, fromsByProvider, new Query.Exists(c.getSubquery())));
        } else if (criterion instanceof HibernateAlias) {
            return null; // Metadata only, handled by JpaQueryContext
        }
        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

    @SuppressWarnings("unchecked")
    private Predicate handleSubqueryCriterion(AbstractQuery<?> criteriaQuery, From<?, ?> root, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity, Query.SubqueryCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        QueryableCriteria<?> qc = c.getValue();

        // If it's a comparison criterion, we expect the subquery to return the same type as the property
        Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();

        // If the subquery has no projections, we default to projecting the SAME property name if available on the subquery entity
        if (qc.getProjections().isEmpty()) {
            PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(c.getProperty());
            if (prop != null) {
                ((QueryableCriteria) qc).getProjections().add(Projections.property(c.getProperty()));
            }
        }

        Query.ProjectionList projectionList = new Query.ProjectionList();
        for (Query.Projection p : qc.getProjections()) {
            projectionList.add(p);
        }

        Subquery<?> subquery = criteriaQuery.subquery(expectedType);
        var creator = new JpaCriteriaQueryCreator(projectionList, criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
        creator.setParentContext(fromsByProvider);

        creator.populateSubquery((JpaSubQuery) subquery);

        if (c instanceof Query.EqualsAll) {
            return criteriaBuilder.equal(propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.NotEqualsAll) {
            return criteriaBuilder.notEqual(propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanAll) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanSome) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.GreaterThanEqualsAll) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.GreaterThanEqualsSome) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.LessThanAll) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.LessThanSome) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.LessThanEqualsAll) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.all(subquery));
        } else if (c instanceof Query.LessThanEqualsSome) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) criteriaBuilder.some(subquery));
        } else if (c instanceof Query.NotIn) {
            return criteriaBuilder.not(propertyPath.in(subquery));
        }

        throw new UnsupportedOperationException("Unsupported subquery criterion: " + c.getClass().getName());
    }

    private Predicate handleJunction(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root_,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.Junction junction) {
        List<Query.Criterion> criteriaList = junction.getCriteria();
        Predicate[] predicates = getPredicates(criteriaQuery, root_, criteriaList, fromsByProvider, entity);
        if (junction instanceof Query.Conjunction) {
            return criteriaBuilder.and(predicates);
        } else if (junction instanceof Query.Disjunction) {
            return criteriaBuilder.or(predicates);
        } else if (junction instanceof Query.Negation) {
            if (predicates.length > 1) {
                throw new IllegalArgumentException("Negation does not support multiple predicates in this context. Use conjunction or disjunction within negation.");
            }
            return criteriaBuilder.not(criteriaBuilder.and(predicates));
        }
        throw new IllegalArgumentException("Unsupported junction: " + junction);
    }

    private Predicate handleAssociationCriteria(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            DetachedAssociationCriteria<?> associationCriteria) {
        String associationName = associationCriteria.getAssociationPath();
        From<?, ?> associationRoot = fromsByProvider.getFrom(associationName);
        if (associationRoot == null) {
            // Check if we already have it in our parent or alias map
            Expression<?> expr = fromsByProvider.getFullyQualifiedExpression(associationName);
            if (expr instanceof From<?,?> from) {
                associationRoot = from;
            } else {
                associationRoot = fromsByProvider.getRoot().join(associationName);
                fromsByProvider.addFrom(associationName, associationRoot);
            }
        }
        
        // Create a nested context for this association
        JpaQueryContext nestedContext = new JpaQueryContext(fromsByProvider, null, associationRoot);
        
        GrailsHibernatePersistentEntity associatedEntity = (GrailsHibernatePersistentEntity) associationCriteria.getAssociation().getAssociatedEntity();
        List<Query.Criterion> criteriaList = associationCriteria.getCriteria();
        return criteriaBuilder.and(getPredicates(criteriaQuery, associationRoot, criteriaList, nestedContext, associatedEntity));
    }

    private Predicate handleHibernateAssociationQuery(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            HibernateAssociationQuery associationQuery) {
        String associationName = associationQuery.associationPath;
        From<?, ?> associationRoot = fromsByProvider.getFrom(associationName);
        if (associationRoot == null) {
            Expression<?> expr = fromsByProvider.getFullyQualifiedExpression(associationName);
            if (expr instanceof From<?,?> from) {
                associationRoot = from;
            } else {
                associationRoot = fromsByProvider.getRoot().join(associationName, JoinType.INNER);
                fromsByProvider.addFrom(associationName, associationRoot);
            }
        }
        
        // Create a nested context for this association
        JpaQueryContext nestedContext = new JpaQueryContext(fromsByProvider, null, associationRoot);

        GrailsHibernatePersistentEntity associatedEntity = associationQuery.getEntity();
        List<Query.Criterion> criteriaList = associationQuery.getAssociationCriteria();
        return criteriaBuilder.and(getPredicates(criteriaQuery, associationRoot, criteriaList, nestedContext, associatedEntity));
    }

    @SuppressWarnings("unchecked")
    private Predicate handlePropertyCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.PropertyCriterion pc) {
        String propertyName = pc.getProperty();
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(propertyName);
        if (propertyPath == null) {
            throw new ConfigurationException("Cannot use comparison criteria on non-existent property [" + propertyName + "] of class [" + entity.getJavaClass().getName() + "]");
        }

        if (pc instanceof Query.Equals) {
            return handleEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity);
        } else if (pc instanceof Query.NotEquals) {
            return handleNotEquals(criteriaQuery, pc, propertyPath, fromsByProvider, entity);
        } else if (pc instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.getValue(), propertyPath));
        } else if (pc instanceof Query.RLike rLike) {
            return handleRLike((Expression<String>) propertyPath, rLike);
        } else if (pc instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) convertValue(entity, propertyName, pc.getValue(), propertyPath));
        } else if (pc instanceof Query.GreaterThan) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath));
        } else if (pc instanceof Query.GreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath));
        } else if (pc instanceof Query.LessThan) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath));
        } else if (pc instanceof Query.LessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) convertComparisonValue(entity, propertyName, pc.getValue(), fromsByProvider, propertyPath));
        } else if (pc instanceof Query.In) {
            Object value = pc.getValue();
            if (value instanceof QueryableCriteria qc) {
                Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();

                // If the subquery has no projections, we default to projecting the SAME property name if available on the subquery entity
                if (qc.getProjections().isEmpty() && propertyPath != null) {
                    PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyName);
                    if (prop != null) {
                        ((QueryableCriteria) qc).getProjections().add(Projections.property(propertyName));
                    }
                }

                Query.ProjectionList projectionList = new Query.ProjectionList();
                for (Object p : qc.getProjections()) {
                    projectionList.add((Query.Projection) p);
                }

                Subquery<?> subquery = criteriaQuery.subquery(expectedType);
                var creator = new JpaCriteriaQueryCreator(projectionList, criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
                creator.setParentContext(fromsByProvider);

                creator.populateSubquery((JpaSubQuery) subquery);
                return propertyPath.in(subquery);
            }

            Collection<?> collection = value instanceof Collection ? (Collection<?>) value : Collections.singletonList(value);
            List<Object> converted = collection.stream()
                    .map(v -> convertValue(entity, propertyName, v, propertyPath))
                    .collect(Collectors.toList());

            if (isCollectionPath(propertyPath)) {
                // For collection properties, we use "member of" for each value joined with OR
                if (converted.isEmpty()) {
                    return criteriaBuilder.disjunction(); // Always false for empty IN on collection
                }
                Predicate[] memberOfPredicates = converted.stream()
                        .map(v -> criteriaBuilder.isMember((Object) v, (Expression) propertyPath))
                        .toArray(Predicate[]::new);
                return criteriaBuilder.or(memberOfPredicates);
            }

            return propertyPath.in(converted);
        } else if (pc instanceof Query.Between between) {
            return criteriaBuilder.between((Expression<? extends Comparable>) propertyPath, (Comparable) convertValue(entity, propertyName, between.getFrom(), propertyPath), (Comparable) convertValue(entity, propertyName, between.getTo(), propertyPath));
        } else if (pc instanceof Query.SizeEquals) {
            return criteriaBuilder.equal(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeNotEquals) {
            return criteriaBuilder.notEqual(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeGreaterThan) {
            return criteriaBuilder.greaterThan(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeGreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeLessThan) {
            return criteriaBuilder.lessThan(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        } else if (pc instanceof Query.SizeLessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo(criteriaBuilder.size((Expression<java.util.Collection<?>>) propertyPath), (Integer) pc.getValue());
        }

        throw new UnsupportedOperationException("Unsupported criterion: " + pc.getClass().getName());
    }

    private Predicate handleRLike(Expression<String> propertyPath, Query.RLike c) {
        String pattern = c.getValue().toString().replaceAll("^/|/$", "");
        return criteriaBuilder.equal(
            criteriaBuilder.function(
                GrailsRLikeFunctionContributor.RLIKE,
                Boolean.class,
                propertyPath,
                criteriaBuilder.literal(pattern)),
            true);
    }

    @SuppressWarnings("unchecked")
    private Predicate handlePropertyComparisonCriterion(JpaQueryContext fromsByProvider, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = fromsByProvider.getFullyQualifiedExpression(c.getProperty());
        Expression<?> otherPropertyPath = fromsByProvider.getFullyQualifiedExpression(c.getOtherProperty());

        if (propertyPath == null || otherPropertyPath == null) {
            return null;
        }

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
            return criteriaBuilder.isEmpty((Expression<java.util.Collection<?>>) propertyPath);
        } else if (((Object)c) instanceof Query.IsNotEmpty) {
            return criteriaBuilder.isNotEmpty((Expression<java.util.Collection<?>>) propertyPath);
        }
        throw new UnsupportedOperationException("Unsupported property name criterion: " + c.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private Predicate handleEquals(AbstractQuery<?> criteriaQuery, Query.PropertyCriterion pc, Expression<?> propertyPath, JpaQueryContext fromsByProvider, GrailsHibernatePersistentEntity entity) {
        if (pc.getValue() instanceof QueryableCriteria qc) {
            Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();
            Subquery<?> subquery = criteriaQuery.subquery(expectedType);
            var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
            creator.setParentContext(fromsByProvider);
            
            if (qc.getProjections().isEmpty() && propertyPath != null) {
                String propertyName = pc.getProperty();
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
                }
                PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyName);
                if (prop != null) {
                    qc.getProjections().add(Projections.property(propertyName));
                }
            }
            
            creator.populateSubquery((JpaSubQuery)subquery);
            return criteriaBuilder.equal(propertyPath, subquery);
        } else {
            return criteriaBuilder.equal(propertyPath, convertComparisonValue(entity, pc.getProperty(), pc.getValue(), fromsByProvider, propertyPath));
        }
    }

    @SuppressWarnings("unchecked")
    private Predicate handleNotEquals(
            AbstractQuery<?> criteriaQuery,
            Query.PropertyCriterion pc,
            Expression<?> propertyPath,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity) {
        Object value = pc.getValue();
        if (value == null) {
            return criteriaBuilder.isNotNull(propertyPath);
        }
        if (value instanceof QueryableCriteria qc) {
            Class<?> expectedType = propertyPath != null ? propertyPath.getJavaType() : qc.getPersistentEntity().getJavaClass();
            Subquery<?> subquery = criteriaQuery.subquery(expectedType);
            var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, (GrailsHibernatePersistentEntity) qc.getPersistentEntity(), (DetachedCriteria) qc, conversionService);
            creator.setParentContext(fromsByProvider);

            if (qc.getProjections().isEmpty() && propertyPath != null) {
                String propertyName = pc.getProperty();
                if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                    propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1];
                }
                PersistentProperty prop = qc.getPersistentEntity().getPropertyByName(propertyName);
                if (prop != null) {
                    qc.getProjections().add(Projections.property(propertyName));
                }
            }

            creator.populateSubquery((JpaSubQuery) subquery);
            return criteriaBuilder.or(criteriaBuilder.notEqual(propertyPath, subquery), criteriaBuilder.isNull(propertyPath));
        }
        return criteriaBuilder.or(
                criteriaBuilder.notEqual(propertyPath, convertComparisonValue(entity, pc.getProperty(), value, fromsByProvider, propertyPath)),
                criteriaBuilder.isNull(propertyPath));
    }

    @SuppressWarnings("unchecked")
    private Predicate handleExists(
            AbstractQuery<?> criteriaQuery,
            JpaQueryContext fromsByProvider,
            Query.Exists exists) {
        QueryableCriteria subqueryCriteria = exists.getSubquery();
        GrailsHibernatePersistentEntity subqueryEntity = (GrailsHibernatePersistentEntity) subqueryCriteria.getPersistentEntity();
        Subquery<?> subquery = criteriaQuery.subquery(subqueryEntity.getJavaClass());
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, subqueryEntity, (DetachedCriteria) subqueryCriteria, conversionService);
        creator.setParentContext(fromsByProvider);
        creator.populateSubquery((JpaSubQuery)subquery);
        return criteriaBuilder.exists(subquery);
    }

    private Object convertValue(GrailsHibernatePersistentEntity entity, String propertyName, Object value, Expression<?> propertyPath) {
        if (value == null) {
            throw new ConfigurationException("Null value for property [" + propertyName + "] is not allowed in comparison criteria.");
        }

        Class<?> targetType = propertyPath != null ? propertyPath.getJavaType() : null;

        if (targetType == null && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName);
            if (prop != null) {
                targetType = prop.getType();
            }
        }
        
        if (targetType != null && Collection.class.isAssignableFrom(targetType) && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName);
            if (prop instanceof HibernateToManyProperty toMany) {
                targetType = toMany.getComponentType();
            }
        }
        
        if (targetType != null && conversionService.canConvert(value.getClass(), targetType)) {
            if (!Collection.class.isAssignableFrom(targetType) || Collection.class.isAssignableFrom(value.getClass())) {
                return conversionService.convert(value, targetType);
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object convertComparisonValue(GrailsHibernatePersistentEntity entity, String propertyName, Object value, JpaQueryContext context, Expression<?> propertyPath) {
        if (value instanceof PropertyArithmetic pa) {
            Expression<Number> left = (Expression<Number>) context.getFullyQualifiedExpression(pa.propertyName());
            Expression<Number> right = (Expression<Number>) criteriaBuilder.literal(pa.operand());
            
            return switch (pa.operator()) {
                case MULTIPLY -> criteriaBuilder.prod(left, right);
                case DIVIDE -> criteriaBuilder.quot(left, right);
                case ADD -> criteriaBuilder.sum(left, right);
                case SUBTRACT -> criteriaBuilder.diff(left, right);
            };
        }
        Object converted = convertValue(entity, propertyName, value, propertyPath);
        if (!(converted instanceof Expression)) {
            return criteriaBuilder.literal(converted);
        }
        return converted;
    }

    public Predicate generate(
            AbstractQuery<?> cq, From<?, ?> root, List<Query.Criterion> criteriaList, JpaQueryContext tablesByName, GrailsHibernatePersistentEntity entity) {
        Predicate[] predicates = getPredicates(cq, root, criteriaList, tablesByName, entity);
        return criteriaBuilder.and(predicates);
    }
}
