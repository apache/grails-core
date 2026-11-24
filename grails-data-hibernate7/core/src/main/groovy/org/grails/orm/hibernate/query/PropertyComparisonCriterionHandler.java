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
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.springframework.util.ClassUtils;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.query.Query;

/**
 * Handles criteria comparing two properties against each other.
 *
 * @since 8.0.1
 */
class PropertyComparisonCriterionHandler implements InternalCriterionHandler<Query.PropertyComparisonCriterion> {

    @Override
    @SuppressWarnings("unchecked")
    public Predicate handle(CriterionContext context, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = context.resolvePath(c.getProperty());
        if (propertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use property comparison criteria on non-existent property [" + c.getProperty() + "] of class [" + className + "]");
        }
        Expression<?> otherPropertyPath = context.resolvePath(c.getOtherProperty());
        if (otherPropertyPath == null) {
            String className = context.getEntity() != null ? context.getEntity().getJavaClass().getName() : "unknown";
            throw new ConfigurationException("Cannot use property comparison criteria on non-existent property [" + c.getOtherProperty() + "] of class [" + className + "]");
        }

        HibernateCriteriaBuilder criteriaBuilder = context.getCriteriaBuilder();

        if ((c instanceof Query.EqualsProperty || c instanceof Query.NotEqualsProperty) &&
                unrelatedEntities(propertyPath, otherPropertyPath)) {
            return compareIdentifiers(criteriaBuilder, (Path<?>) propertyPath, (Path<?>) otherPropertyPath,
                    c instanceof Query.NotEqualsProperty);
        }

        Expression<?> left = identifierIfCompared(propertyPath, otherPropertyPath);
        Expression<?> right = identifierIfCompared(otherPropertyPath, propertyPath);
        propertyPath = left;
        otherPropertyPath = right;

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

    /**
     * An association compared with a value that is not an instance of it, such as {@code id == team.club},
     * is compared by its identifier, as Hibernate 5 does. Two associations are compared as entities, whatever
     * their classes: an identifier cannot be taken out of both when it is composite.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Expression<?> identifierIfCompared(Expression<?> expression, Expression<?> other) {
        if (expression instanceof Path<?> path &&
                path.getModel() instanceof SingularAttribute<?, ?> attribute &&
                attribute.getType() instanceof EntityType<?> entityType &&
                !isEntityValued(other) &&
                !path.getJavaType().isAssignableFrom(other.getJavaType()) &&
                !other.getJavaType().isAssignableFrom(path.getJavaType())) {
            Class idType = entityType.getIdType().getJavaType();
            return path.get(entityType.getId(idType).getName());
        }
        return expression;
    }

    private static boolean isEntityValued(Expression<?> expression) {
        return expression instanceof Path<?> path && (path.getModel() instanceof EntityType<?> ||
                path.getModel() instanceof SingularAttribute<?, ?> attribute && attribute.getType() instanceof EntityType<?>);
    }

    private static EntityType<?> entityTypeOf(Expression<?> expression) {
        if (expression instanceof Path<?> path) {
            if (path.getModel() instanceof EntityType<?> entityType) {
                return entityType;
            }
            if (path.getModel() instanceof SingularAttribute<?, ?> attribute && attribute.getType() instanceof EntityType<?> entityType) {
                return entityType;
            }
        }
        return null;
    }

    private static boolean unrelatedEntities(Expression<?> left, Expression<?> right) {
        return entityTypeOf(left) != null && entityTypeOf(right) != null &&
                !left.getJavaType().isAssignableFrom(right.getJavaType()) &&
                !right.getJavaType().isAssignableFrom(left.getJavaType());
    }

    /**
     * Hibernate does not compare two entities of classes that are not related, so two associations to such classes
     * are compared by their identifiers, one part at a time: a generated identifier is one part, and a composite
     * identifier has one part for each of its properties, paired in the order of their names. This is what
     * Hibernate 5 does with the columns of the two foreign keys. Identifiers with a different number of parts, or with parts
     * of types that are never equal, are never equal, so such a comparison matches no row, and its negation matches the rows
     * that have both associations.
     */
    private static Predicate compareIdentifiers(HibernateCriteriaBuilder criteriaBuilder, Path<?> left, Path<?> right, boolean negate) {
        List<Expression<?>> leftParts = identifierParts(left, entityTypeOf(left));
        List<Expression<?>> rightParts = identifierParts(right, entityTypeOf(right));
        if (!comparable(leftParts, rightParts)) {
            // identifiers of different shapes are never equal: nothing matches ==, and every row that has both associations
            // matches !=, a null being unknown as it is in SQL
            return negate ? criteriaBuilder.and(criteriaBuilder.isNotNull(left), criteriaBuilder.isNotNull(right)) :
                    criteriaBuilder.disjunction();
        }
        for (int i = 0; i < leftParts.size(); i++) {
            refuseInexactNumbers(leftParts.get(i).getJavaType(), rightParts.get(i).getJavaType());
        }
        List<Predicate> parts = new ArrayList<>();
        for (int i = 0; i < leftParts.size(); i++) {
            parts.add(negate ? criteriaBuilder.notEqual(leftParts.get(i), rightParts.get(i)) :
                    criteriaBuilder.equal(leftParts.get(i), rightParts.get(i)));
        }
        Predicate[] predicates = parts.toArray(new Predicate[0]);
        return negate ? criteriaBuilder.or(predicates) : criteriaBuilder.and(predicates);
    }

    /**
     * Whether two identifiers have the same number of parts and, part by part, types that a database compares: numbers of
     * any width with each other, and otherwise a type with itself or with its subtype. A text part and a number part are
     * never equal, so identifiers that differ in a part's type are not compared at all, as a different number of parts is not.
     */
    private static boolean comparable(List<Expression<?>> leftParts, List<Expression<?>> rightParts) {
        if (leftParts.size() != rightParts.size()) {
            return false;
        }
        for (int i = 0; i < leftParts.size(); i++) {
            if (!comparableTypes(leftParts.get(i).getJavaType(), rightParts.get(i).getJavaType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * A database compares an integral number with a floating point one by converting the integral one, which rounds a
     * value that a double cannot hold, so two parts that are not the same value can compare equal. Such a comparison is
     * refused and not answered wrongly.
     */
    private static void refuseInexactNumbers(Class<?> left, Class<?> right) {
        boolean numbers = Number.class.isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(left)) &&
                Number.class.isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(right));
        if (numbers && floatingPoint(left) != floatingPoint(right)) {
            throw new IllegalArgumentException("Cannot compare identifier parts of type " + left.getName() + " and " +
                    right.getName() + ": a floating point number and an integral one are not compared exactly");
        }
    }

    private static boolean floatingPoint(Class<?> type) {
        Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(type);
        return Float.class.equals(boxed) || Double.class.equals(boxed);
    }

    private static boolean comparableTypes(Class<?> left, Class<?> right) {
        Class<?> a = ClassUtils.resolvePrimitiveIfNecessary(left);
        Class<?> b = ClassUtils.resolvePrimitiveIfNecessary(right);
        return a.isAssignableFrom(b) || b.isAssignableFrom(a) || Number.class.isAssignableFrom(a) && Number.class.isAssignableFrom(b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Expression<?>> identifierParts(Path<?> path, EntityType<?> entityType) {
        List<Expression<?>> parts = new ArrayList<>();
        if (entityType.hasSingleIdAttribute()) {
            SingularAttribute<?, ?> id = entityType.getId((Class) entityType.getIdType().getJavaType());
            Path<?> idPath = path.get(id.getName());
            if (id.getType() instanceof EmbeddableType<?> embeddable) {
                List<Attribute<?, ?>> attributes = new ArrayList<>(embeddable.getAttributes());
                attributes.sort(Comparator.comparing(Attribute::getName));
                for (Attribute<?, ?> attribute : attributes) {
                    addPart(parts, idPath.get(attribute.getName()), attribute);
                }
            }
            else {
                addPart(parts, idPath, id);
            }
        }
        else {
            // a composite identifier without an identifier class: its properties are the properties of the entity
            List<SingularAttribute<?, ?>> ids = new ArrayList<>();
            for (SingularAttribute<?, ?> attribute : entityType.getSingularAttributes()) {
                if (attribute.isId()) {
                    ids.add(attribute);
                }
            }
            ids.sort(Comparator.comparing(Attribute::getName));
            for (SingularAttribute<?, ?> attribute : ids) {
                addPart(parts, path.get(attribute.getName()), attribute);
            }
        }
        return parts;
    }

    /**
     * A part of an identifier that is an association is compared by the identifier of the associated entity, in turn
     * one part at a time, as the foreign key of it is: Hibernate does not compare two such entities of classes that are
     * not related, whatever their identifiers.
     */
    private static void addPart(List<Expression<?>> parts, Path<?> part, Attribute<?, ?> attribute) {
        if (attribute instanceof SingularAttribute<?, ?> singular && singular.getType() instanceof EntityType<?> associated) {
            parts.addAll(identifierParts(part, associated));
        }
        else {
            parts.add(part);
        }
    }
}
