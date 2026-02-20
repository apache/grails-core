package org.grails.orm.hibernate.query;

import groovy.util.logging.Slf4j;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.predicate.SqmInListPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public class PredicateGenerator {
    private static final Logger log = LoggerFactory.getLogger(PredicateGenerator.class);

    public Predicate[] getPredicates(HibernateCriteriaBuilder cb,
                                     CriteriaQuery criteriaQuery,
                                     From root_,
                                     List criteriaList, 
                                     JpaFromProvider fromsByProvider, 
                                     PersistentEntity entity) {

        List<Predicate> list = ((List<Object>) criteriaList).stream()
                .map(criterion -> handleCriterion(cb, criteriaQuery, root_, fromsByProvider, entity, criterion))
                .filter(Objects::nonNull)
                .toList();

        if (list.isEmpty()) {
            list = List.of(cb.equal(cb.literal(1), cb.literal(1)));
        }
        return list.toArray(new Predicate[0]);
    }

    private Predicate handleCriterion(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, From root_, JpaFromProvider fromsByProvider, PersistentEntity entity, Object criterion) {
        if (criterion instanceof Query.Junction junction) {
            return handleJunction(cb, criteriaQuery, root_, fromsByProvider, entity, junction);
        } else if (criterion instanceof Query.DistinctProjection) {
            return cb.conjunction();
        } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
            return handleAssociationCriteria(cb, criteriaQuery, root_, fromsByProvider, entity, c);
        } else if (criterion instanceof Query.PropertyCriterion pc) {
            return handlePropertyCriterion(cb, criteriaQuery, root_, fromsByProvider, entity, pc);
        } else if (criterion instanceof Query.PropertyComparisonCriterion c) {
            return handlePropertyComparisonCriterion(cb, fromsByProvider, root_, c);
        } else if (criterion instanceof Query.PropertyNameCriterion c) {
            return handlePropertyNameCriterion(cb, fromsByProvider, c);
        } else if (criterion instanceof Query.Exists c) {
            return handleExists(cb, criteriaQuery, root_, fromsByProvider, entity, c);
        } else if (criterion instanceof Query.NotExists c) {
            return cb.not(handleExists(cb, criteriaQuery, root_, fromsByProvider, entity, new Query.Exists(c.getSubquery())));
        }
        throw new IllegalArgumentException("Unsupported criterion: " + criterion);
    }

    private Predicate handleJunction(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, From root_, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.Junction junction) {
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

    private Predicate handleAssociationCriteria(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, From root_, JpaFromProvider fromsByProvider, PersistentEntity entity, DetachedAssociationCriteria<?> c) {
        Join child = root_.join(c.getAssociationPath(), JoinType.LEFT);
        JpaFromProvider childTablesByName = (JpaFromProvider) fromsByProvider.clone();
        childTablesByName.put("root", child);
        return cb.and(getPredicates(cb, criteriaQuery, child, c.getCriteria(), childTablesByName, entity));
    }

    private Predicate handlePropertyCriterion(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, From root_, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.PropertyCriterion pc) {
        var fullyQualifiedPath = fromsByProvider.getFullyQualifiedPath(pc.getProperty());

        if (pc instanceof Query.NotIn c) {
            return handleNotIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
        } else if (pc instanceof Query.SubqueryCriterion c) {
            return handleSubqueryCriterion(cb, criteriaQuery, fromsByProvider, entity, c);
        } else if (pc instanceof Query.In c) {
            return handleIn(cb, criteriaQuery, fromsByProvider, entity, c, fullyQualifiedPath);
        } else if (pc instanceof Query.ILike c) {
            return cb.ilike(fullyQualifiedPath, c.getValue().toString());
        } else if (pc instanceof Query.RLike c) {
            return handleRLike(cb, fullyQualifiedPath, c);
        } else if (pc instanceof Query.Like c) {
            return cb.like(fullyQualifiedPath, c.getValue().toString());
        } else if (pc instanceof Query.Equals c) {
            return cb.equal(fullyQualifiedPath, c.getValue());
        } else if (pc instanceof Query.NotEquals c) {
            return cb.or(cb.notEqual(fullyQualifiedPath, c.getValue()), cb.isNull(fullyQualifiedPath));
        } else if (pc instanceof Query.IdEquals c) {
            return cb.equal(root_.get("id"), c.getValue());
        } else if (pc instanceof Query.GreaterThan c) {
            return cb.gt(fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.GreaterThanEquals c) {
            return cb.ge(fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.LessThan c) {
            return cb.lt(fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.LessThanEquals c) {
            return cb.le(fullyQualifiedPath, getNumericValue(c));
        } else if (pc instanceof Query.SizeEquals c) {
            return cb.equal(cb.size(fullyQualifiedPath), c.getValue());
        } else if (pc instanceof Query.SizeNotEquals c) {
            return cb.notEqual(cb.size(fullyQualifiedPath), c.getValue());
        } else if (pc instanceof Query.SizeGreaterThan c) {
            return cb.gt(cb.size(fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeGreaterThanEquals c) {
            return cb.ge(cb.size(fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeLessThan c) {
            return cb.lt(cb.size(fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.SizeLessThanEquals c) {
            return cb.le(cb.size(fullyQualifiedPath), getNumericValue(c));
        } else if (pc instanceof Query.Between c) {
            return cb.between(fullyQualifiedPath, (Comparable) c.getFrom(), (Comparable) c.getTo());
        }
        return null;
    }

    private Predicate handleNotIn(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.NotIn c, Path fullyQualifiedPath) {
        var queryableCriteria = getQueryableCriteriaFromInCriteria(c);
        if (Objects.nonNull(queryableCriteria)) {
            return cb.not(getQueryableCriteriaValue(cb, criteriaQuery, fromsByProvider, entity, c, queryableCriteria));
        } else if (Objects.nonNull(c.getSubquery()) && !c.getSubquery().getProjections().isEmpty()) {
            Subquery subquery2 = criteriaQuery.subquery(Number.class);
            Root from2 = subquery2.from(c.getValue().getPersistentEntity().getJavaClass());
            JpaFromProvider newMap2 = (JpaFromProvider) fromsByProvider.clone();
            var projection = c.getSubquery().getProjections().get(0);
            if (projection instanceof Query.PropertyProjection pp) {
                boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                Predicate[] predicates2 = getPredicates(cb, criteriaQuery, from2, c.getValue().getCriteria(), newMap2, entity);
                subquery2.select(from2.get(pp.getPropertyName())).distinct(distinct).where(cb.and(predicates2));
                return cb.not(cb.in(fullyQualifiedPath).value(subquery2));
            } else if (projection instanceof Query.IdProjection) {
                Predicate[] predicates2 = getPredicates(cb, criteriaQuery, from2, c.getValue().getCriteria(), newMap2, entity);
                subquery2.select(from2).where(cb.and(predicates2));
                return cb.not(cb.in(fullyQualifiedPath).value(subquery2));
            }
        }
        return cb.not(cb.in(fullyQualifiedPath, c.getValue()));
    }

    private Predicate handleIn(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.In c, Path fullyQualifiedPath) {
        var queryableCriteria = getQueryableCriteriaFromInCriteria(c);
        if (Objects.nonNull(queryableCriteria)) {
            return getQueryableCriteriaValue(cb, criteriaQuery, fromsByProvider, entity, c, queryableCriteria);
        } else if (!c.getValues().isEmpty()) {
            boolean areGormEntities = c.getValues().stream().allMatch(GormEntity.class::isInstance);
            if (areGormEntities) {
                List<GormEntity> gormEntities = new ArrayList<>(c.getValues());
                Path id = criteriaQuery.from(gormEntities.get(0).getClass()).get("id");
                Collection newValues = gormEntities.stream().map(GormEntity::ident).toList();
                return cb.in(id, newValues);
            }
            return cb.in(fullyQualifiedPath, c.getValues());
        }
        return null;
    }

    private Predicate handleRLike(HibernateCriteriaBuilder cb, Path fullyQualifiedPath, Query.RLike c) {
        String pattern = c.getPattern().replaceAll("^/|/$", "");
        return cb.equal(cb.function(GrailsRLikeFunctionContributor.RLIKE, Boolean.class, fullyQualifiedPath, cb.literal(pattern)), true);
    }

    private Predicate handleSubqueryCriterion(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.SubqueryCriterion c) {
        Subquery subquery = criteriaQuery.subquery(Number.class);
        Root from = subquery.from(c.getValue().getPersistentEntity().getJavaClass());
        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
        newMap.put("root", from);
        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getValue().getCriteria(), newMap, entity);
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

    private Predicate handlePropertyComparisonCriterion(HibernateCriteriaBuilder cb, JpaFromProvider fromsByProvider, From root_, Query.PropertyComparisonCriterion c) {
        Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());
        Path otherPath = root_.get(c.getOtherProperty());
        if (c instanceof Query.EqualsProperty) return cb.equal(path, otherPath);
        if (c instanceof Query.NotEqualsProperty) return cb.notEqual(path, otherPath);
        if (c instanceof Query.LessThanEqualsProperty) return cb.le(path, otherPath);
        if (c instanceof Query.LessThanProperty) return cb.lt(path, otherPath);
        if (c instanceof Query.GreaterThanEqualsProperty) return cb.ge(path, otherPath);
        if (c instanceof Query.GreaterThanProperty) return cb.gt(path, otherPath);
        return null;
    }

    private Predicate handlePropertyNameCriterion(HibernateCriteriaBuilder cb, JpaFromProvider fromsByProvider, Query.PropertyNameCriterion c) {
        Path path = fromsByProvider.getFullyQualifiedPath(c.getProperty());
        if (c instanceof Query.IsNull) return cb.isNull(path);
        if (c instanceof Query.IsNotNull) return cb.isNotNull(path);
        if (c instanceof Query.IsEmpty) return cb.isEmpty(path);
        if (c instanceof Query.IsNotEmpty) return cb.isNotEmpty(path);
        return null;
    }

    private Predicate handleExists(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, From root_, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.Exists c) {
        Subquery subquery = criteriaQuery.subquery(Integer.class);
        PersistentEntity childPersistentEntity = c.getSubquery().getPersistentEntity();
        Root subRoot = subquery.from(childPersistentEntity.getJavaClass());
        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
        newMap.put("root", subRoot);
        var predicates = getPredicates(cb, criteriaQuery, subRoot, c.getSubquery().getCriteria(), newMap, entity);
        var existsPredicate = getExistsPredicate(cb, root_, childPersistentEntity, subRoot);
        Predicate[] allPredicates = Stream.concat(Arrays.stream(predicates), Stream.of(existsPredicate)).toArray(Predicate[]::new);
        subquery.select(cb.literal(1)).where(cb.and(allPredicates));
        return cb.exists(subquery);
    }

    private CriteriaBuilder.In getQueryableCriteriaValue(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.PropertyNameCriterion criterion, QueryableCriteria queryableCriteria) {
        var projection = findPropertyOrIdProjection(queryableCriteria);
        var subProperty = findSubproperty(projection);
        var path = fromsByProvider.getFullyQualifiedPath(criterion.getProperty());
        var in = findInPredicate(cb, projection, path, subProperty);
        var subquery = criteriaQuery.subquery(getJavaTypeOfInClause((SqmInListPredicate) in));
        var from = subquery.from(queryableCriteria.getPersistentEntity().getJavaClass());
        var clonedProviderByName = (JpaFromProvider) fromsByProvider.clone();
        clonedProviderByName.put("root", from);
        var predicates = getPredicates(cb, criteriaQuery, from, queryableCriteria.getCriteria(), clonedProviderByName, entity);
        subquery.select(clonedProviderByName.getFullyQualifiedPath(subProperty)).distinct(true).where(cb.and(predicates));
        return in.value(subquery);
    }

    private Predicate getExistsPredicate(HibernateCriteriaBuilder cb, From root_, PersistentEntity childPersistentEntity, Root subRoot) {
        Association owner = childPersistentEntity.getAssociations().stream()
                .filter(assoc -> assoc.getAssociatedEntity().getJavaClass().equals(root_.getJavaType()))
                .findFirst().orElseThrow();
        return cb.equal(subRoot.get(owner.getName()), root_);
    }

    @SuppressWarnings("rawtypes")
    private JpaInPredicate findInPredicate(HibernateCriteriaBuilder cb, Object projection, Path path, String subProperty) {
        return projection instanceof Query.PropertyProjection ? cb.in(path) : cb.in(((SqmPath) path).get(subProperty));
    }

    private String findSubproperty(Object projection) {
        return projection instanceof Query.PropertyProjection ? ((Query.PropertyProjection) projection).getPropertyName() : "id";
    }

    @SuppressWarnings("unchecked")
    private Query.Projection findPropertyOrIdProjection(QueryableCriteria queryableCriteria) {
        return (Query.Projection) queryableCriteria.getProjections().stream()
                .filter(p -> p instanceof Query.PropertyProjection || p instanceof Query.IdProjection)
                .findFirst().orElse(new Query.IdProjection());
    }

    @SuppressWarnings("rawtypes")
    private QueryableCriteria getQueryableCriteriaFromInCriteria(Query.Criterion criterion) {
        return criterion instanceof Query.In ? ((Query.In) criterion).getSubquery() : ((Query.NotIn) criterion).getSubquery();
    }

    @SuppressWarnings("rawtypes")
    private Class getJavaTypeOfInClause(SqmInListPredicate predicate) {
        return Optional.ofNullable(predicate.getTestExpression().getExpressible())
                .map(expressible -> expressible.getExpressibleJavaType().getJavaTypeClass())
                .orElse(null);
    }

    private Number getNumericValue(Query.PropertyCriterion criterion) {
        Object value = criterion.getValue();
        if (value instanceof Number num) return num;
        throw new ConfigurationException(String.format("Operation '%s' on property '%s' only accepts a numeric value, but received a %s",
                criterion.getClass().getSimpleName(), criterion.getProperty(), (value == null ? "null" : value.getClass().getName())));
    }
}
