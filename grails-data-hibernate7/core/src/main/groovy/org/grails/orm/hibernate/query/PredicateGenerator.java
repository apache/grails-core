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
import java.util.Optional;
import java.util.stream.Stream;

import groovy.util.logging.Slf4j;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.predicate.SqmInListPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;

@Slf4j
@SuppressWarnings({
  "PMD.DataflowAnomalyAnalysis",
  "PMD.AvoidLiteralsInIfCondition",
  "PMD.AvoidDuplicateLiterals",
  // GORM stores criterion values as Object; JPA expects types resolved at compile time.
  // The unchecked casts here are deliberate — type safety is enforced at runtime by
  // MappingContext, not at compile time. This is the inherent cost of bridging a
  // runtime-typed DSL (GORM) to a compile-time-typed API (JPA Criteria).
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

    public PredicateGenerator(ConversionService conversionService) {
        this.conversionService = conversionService;
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
      return handleAssociationCriteria(cb, criteriaQuery, root, fromsByProvider, entity, c);
    } else if (criterion instanceof HibernateAssociationQuery haq) {
      return handleHibernateAssociationQuery(cb, criteriaQuery, root, fromsByProvider, haq);
    } else if (criterion instanceof Query.PropertyCriterion pc) {
      return handlePropertyCriterion(cb, criteriaQuery, root, fromsByProvider, entity, pc);
    } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
      return handlePropertyComparisonCriterion(cb, fromsByProvider, c);
    } else if (criterion instanceof Query.PropertyNameCriterion c) {
      return handlePropertyNameCriterion(cb, fromsByProvider, c);
    } else if (criterion instanceof Query.Exists c) {
      return handleExists(cb, criteriaQuery, root, fromsByProvider, entity, c);
    } else if (criterion instanceof Query.NotExists c) {
      return cb.not(
          handleExists(
              cb, criteriaQuery, root, fromsByProvider, entity, new Query.Exists(c.getSubquery())));
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
            return handleAssociationCriteria(cb, criteriaQuery, root, fromsByProvider, entity, c);
        } else if (criterion instanceof HibernateAssociationQuery haq) {
            return handleHibernateAssociationQuery(cb, criteriaQuery, root, fromsByProvider, haq);
        } else if (criterion instanceof Query.PropertyCriterion pc) {
            return handlePropertyCriterion(cb, criteriaQuery, root, fromsByProvider, entity, pc);
        } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
            return handlePropertyComparisonCriterion(cb, fromsByProvider, c);
        } else if (criterion instanceof Query.PropertyNameCriterion c) {
            return handlePropertyNameCriterion(cb, fromsByProvider, c);
        } else if (criterion instanceof Query.Exists c) {
            return handleExists(cb, criteriaQuery, root, fromsByProvider, entity, c);
        } else if (criterion instanceof Query.NotExists c) {
            return cb.not(
                    handleExists(cb, criteriaQuery, root, fromsByProvider, entity, new Query.Exists(c.getSubquery())));
        }
        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

  private Predicate handleAssociationCriteria(
      HibernateCriteriaBuilder cb,
      CriteriaQuery<?> criteriaQuery,
      From<?, ?> root,
      JpaFromProvider fromsByProvider,
      PersistentEntity entity,
      DetachedAssociationCriteria<?> c) {
    var child = root.join(c.getAssociationPath(), JoinType.LEFT);
    JpaFromProvider childTablesByName = (JpaFromProvider) fromsByProvider.clone();
    childTablesByName.put("root", child);
    return cb.and(
        getPredicates(cb, criteriaQuery, child, c.getCriteria(), childTablesByName, entity));
  }

  private Predicate handleHibernateAssociationQuery(
      HibernateCriteriaBuilder cb,
      CriteriaQuery<?> criteriaQuery,
      From<?, ?> root,
      JpaFromProvider fromsByProvider,
      HibernateAssociationQuery haq) {
    var child = root.join(haq.associationPath, JoinType.LEFT);
    JpaFromProvider childFroms = (JpaFromProvider) fromsByProvider.clone();
    childFroms.put("root", child);
    return cb.and(
        getPredicates(
            cb, criteriaQuery, child, haq.getAssociationCriteria(), childFroms, haq.getEntity()));
  }

  private Predicate handlePropertyCriterion(
      HibernateCriteriaBuilder cb,
      CriteriaQuery<?> criteriaQuery,
      From<?, ?> root,
      JpaFromProvider fromsByProvider,
      PersistentEntity entity,
      Query.PropertyCriterion pc) {
    var fullyQualifiedPath = fromsByProvider.getFullyQualifiedPath(pc.getProperty());

    if (pc instanceof Query.NotIn c) {
      return handleNotIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
    } else if (pc instanceof Query.SubqueryCriterion c) {
      return handleSubqueryCriterion(cb, criteriaQuery, fromsByProvider, entity, c);
    } else if (pc instanceof Query.In c) {
      return handleIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
    } else if (pc instanceof Query.ILike c) {
      return cb.ilike((Expression<String>) fullyQualifiedPath, c.getValue().toString());
    } else if (pc instanceof Query.RLike c) {
      return handleRLike(cb, fullyQualifiedPath, c);
    } else if (pc instanceof Query.Like c) {
      return cb.like((Expression<String>) fullyQualifiedPath, c.getValue().toString());
    } else if (pc instanceof Query.Equals c) {
      return cb.equal(fullyQualifiedPath, c.getValue());
    } else if (pc instanceof Query.NotEquals c) {
      return cb.or(cb.notEqual(fullyQualifiedPath, c.getValue()), cb.isNull(fullyQualifiedPath));
    } else if (pc instanceof Query.IdEquals c) {
      return cb.equal(root.get("id"), c.getValue());
    } else if (pc instanceof Query.GreaterThan c) {
      return cb.gt((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
    } else if (pc instanceof Query.GreaterThanEquals c) {
      return cb.ge((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
    } else if (pc instanceof Query.LessThan c) {
      return cb.lt((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
    } else if (pc instanceof Query.LessThanEquals c) {
      return cb.le((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
    } else if (pc instanceof Query.SizeEquals c) {
      return cb.equal(cb.size((Expression) fullyQualifiedPath), c.getValue());
    } else if (pc instanceof Query.SizeNotEquals c) {
      return cb.notEqual(cb.size((Expression) fullyQualifiedPath), c.getValue());
    } else if (pc instanceof Query.SizeGreaterThan c) {
      return cb.gt(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
    } else if (pc instanceof Query.SizeGreaterThanEquals c) {
      return cb.ge(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
    } else if (pc instanceof Query.SizeLessThan c) {
      return cb.lt(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
    } else if (pc instanceof Query.SizeLessThanEquals c) {
      return cb.le(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
    } else if (pc instanceof Query.Between c) {
      return cb.between(
          (Expression) fullyQualifiedPath, (Comparable) c.getFrom(), (Comparable) c.getTo());
    }

    private Predicate handleAssociationCriteria(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            DetachedAssociationCriteria<?> c) {
        var child = root.join(c.getAssociationPath(), JoinType.LEFT);
        JpaFromProvider childTablesByName = (JpaFromProvider) fromsByProvider.clone();
        childTablesByName.put("root", child);
        return cb.and(getPredicates(cb, criteriaQuery, child, c.getCriteria(), childTablesByName, entity));
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
      return getQueryableCriteriaValue(
          cb, criteriaQuery, fromsByProvider, entity, c, queryableCriteria);
    } else if (!c.getValues().isEmpty()) {
      if (c.getValues().iterator().next() instanceof GormEntity firstEntity) {
        List<GormEntity> gormEntities = new ArrayList<>(c.getValues());
        Path id = criteriaQuery.from(firstEntity.getClass()).get("id");
        Collection newValues = gormEntities.stream().map(GormEntity::ident).toList();
        return cb.in(id, newValues);
      }
      return cb.in(fullyQualifiedPath, c.getValues());
    }

    private Predicate handlePropertyCriterion(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.PropertyCriterion pc) {
        var fullyQualifiedPath = fromsByProvider.getFullyQualifiedPath(pc.getProperty());

        if (pc instanceof Query.NotIn c) {
            return handleNotIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
        } else if (pc instanceof Query.SubqueryCriterion c) {
            return handleSubqueryCriterion(cb, criteriaQuery, fromsByProvider, entity, c);
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
            return cb.equal(fullyQualifiedPath, c.getValue());
        } else if (pc instanceof Query.NotEquals c) {
            return cb.or(cb.notEqual(fullyQualifiedPath, c.getValue()), cb.isNull(fullyQualifiedPath));
        } else if (pc instanceof Query.IdEquals c) {
            return cb.equal(root.get("id"), c.getValue());
        } else if (pc instanceof Query.GreaterThan c) {
            return cb.gt((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.GreaterThanEquals c) {
            return cb.ge((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.LessThan c) {
            return cb.lt((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.LessThanEquals c) {
            return cb.le((Expression<? extends Number>) fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.SizeEquals c) {
            return cb.equal(cb.size((Expression) fullyQualifiedPath), c.getValue());
        } else if (pc instanceof Query.SizeNotEquals c) {
            return cb.notEqual(cb.size((Expression) fullyQualifiedPath), c.getValue());
        } else if (pc instanceof Query.SizeGreaterThan c) {
            return cb.gt(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeGreaterThanEquals c) {
            return cb.ge(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeLessThan c) {
            return cb.lt(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeLessThanEquals c) {
            return cb.le(cb.size((Expression) fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.Between c) {
            return cb.between((Expression) fullyQualifiedPath, (Comparable) c.getFrom(), (Comparable) c.getTo());
        }
        return null;
    }

  private Predicate handlePropertyComparisonCriterion(
      HibernateCriteriaBuilder cb,
      JpaFromProvider fromsByProvider,
      Query.PropertyComparisonCriterion c) {
    Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());
    Path otherPath = fromsByProvider.getFullyQualifiedPath(c.getOtherProperty());
    // Resolve entity/scalar type mismatch for correlated subquery comparisons (e.g. Club.id == t.club):
    // walk back to the parent entity so we can use entity equality instead of scalar equality.
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
            return cb.in(fullyQualifiedPath, c.getValues());
        }
        return null;
    }

  private Predicate handleExists(
      HibernateCriteriaBuilder cb,
      CriteriaQuery<?> criteriaQuery,
      From<?, ?> root_,
      JpaFromProvider fromsByProvider,
      PersistentEntity entity,
      Query.Exists c) {
    Subquery subquery = criteriaQuery.subquery(Integer.class);
    PersistentEntity childPersistentEntity = c.getSubquery().getPersistentEntity();
    Root subRoot = subquery.from(childPersistentEntity.getJavaClass());
    JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
    newMap.put("root", subRoot);
    var predicates =
        getPredicates(cb, criteriaQuery, subRoot, c.getSubquery().getCriteria(), newMap, entity);
    var existsPredicate = getExistsPredicate(cb, root_, childPersistentEntity, subRoot);
    Predicate[] allPredicates =
        existsPredicate != null
            ? Stream.concat(Arrays.stream(predicates), Stream.of(existsPredicate))
                .toArray(Predicate[]::new)
            : predicates;
    subquery.select(cb.literal(1)).where(cb.and(allPredicates));
    return cb.exists(subquery);
  }

    private Predicate handleSubqueryCriterion(
            HibernateCriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            JpaFromProvider fromsByProvider,
            PersistentEntity entity,
            Query.SubqueryCriterion c) {
        Subquery subquery = criteriaQuery.subquery(Number.class);
        Root from = subquery.from(c.getValue().getPersistentEntity().getJavaClass());
        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
        newMap.put("root", from);
        Predicate[] predicates =
                getPredicates(cb, criteriaQuery, from, c.getValue().getCriteria(), newMap, entity);
        Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());

  private Predicate getExistsPredicate(
      HibernateCriteriaBuilder cb,
      From<?, ?> root_,
      PersistentEntity childPersistentEntity,
      Root subRoot) {
    return childPersistentEntity.getAssociations().stream()
        .filter(assoc -> assoc.getAssociatedEntity().getJavaClass().equals(root_.getJavaType()))
        .findFirst()
        .map(owner -> (Predicate) cb.equal(subRoot.get(owner.getName()), root_))
        .orElse(null);
  }

  private JpaInPredicate findInPredicate(
      HibernateCriteriaBuilder cb, Object projection, Path path, String subProperty) {
    return projection instanceof Query.PropertyProjection
        ? cb.in(path)
        : cb.in(((SqmPath) path).get(subProperty));
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

  private Query.Projection findPropertyOrIdProjection(QueryableCriteria queryableCriteria) {
    return (Query.Projection)
        queryableCriteria.getProjections().stream()
            .filter(p -> p instanceof Query.PropertyProjection || p instanceof Query.IdProjection)
            .findFirst()
            .orElse(new Query.IdProjection());
  }

  private QueryableCriteria getQueryableCriteriaFromInCriteria(Query.Criterion criterion) {
    return criterion instanceof Query.In
        ? ((Query.In) criterion).getSubquery()
        : ((Query.NotIn) criterion).getSubquery();
  }

  private Class getJavaTypeOfInClause(SqmInListPredicate predicate) {
    return Optional.ofNullable(predicate.getTestExpression().getExpressible())
        .map(expressible -> expressible.getExpressibleJavaType().getJavaTypeClass())
        .orElse(null);
  }

  private Number getNumericValue(Query.PropertyCriterion criterion) {
    Object value = criterion.getValue();
    if (value instanceof Number num) return num;
    if (value != null && conversionService.canConvert(value.getClass(), Number.class)) {
      try {
        return conversionService.convert(value, Number.class);
      } catch (org.springframework.core.convert.ConversionException ignored) {
        // fall through to ConfigurationException
      }
    }
    throw new ConfigurationException(
        String.format(
            "Operation '%s' on property '%s' only accepts a numeric value, but received a %s",
            criterion.getClass().getSimpleName(),
            criterion.getProperty(),
            (value == null ? "null" : value.getClass().getName())));
  }
}
