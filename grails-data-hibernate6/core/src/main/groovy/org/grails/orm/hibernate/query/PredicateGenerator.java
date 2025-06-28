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
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.criteria.JpaJoin;
import org.hibernate.query.criteria.JpaPath;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.domain.SqmSetJoin;
import org.hibernate.query.sqm.tree.predicate.SqmInListPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;


@Slf4j
public class PredicateGenerator {
    private static final Logger log = LoggerFactory.getLogger(PredicateGenerator.class);

    public static Predicate[] getPredicates(HibernateCriteriaBuilder cb,
                                            CriteriaQuery criteriaQuery,
                                            From root_,
                                            List<Query.Criterion> criteriaList, JpaFromProvider fromsByProvider) {


        List<Predicate> list = criteriaList.stream().
                map(criterion -> {
                    if (criterion instanceof Query.Disjunction) {
                        List<Query.Criterion> criterionList = ((Query.Disjunction) criterion).getCriteria();
                        return cb.or(getPredicates(cb, criteriaQuery, root_, criterionList, fromsByProvider));

                    } else if (criterion instanceof Query.Conjunction) {
                        List<Query.Criterion> criterionList = ((Query.Conjunction) criterion).getCriteria();
                        return cb.and(getPredicates(cb, criteriaQuery, root_, criterionList, fromsByProvider));
                    } else if (criterion instanceof Query.Negation) {
                        List<Query.Criterion> criterionList = ((Query.Negation) criterion).getCriteria();
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, root_, criterionList, fromsByProvider);
                        if (predicates.length != 1) {
                            log.error("Must have a single predicate behind a not");
                            throw new RuntimeException("Must have a single predicate behind a not");
                        }
                        return cb.not(predicates[0]);
                    } else if (criterion instanceof Query.DistinctProjection) {
                        // this returns always true
                        return cb.conjunction();
                    } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
                            Join child = root_.join(c.getAssociationPath(), JoinType.LEFT);
                            List<Query.Criterion> criterionList = c.getCriteria();
                            JpaFromProvider childTablesByName = (JpaFromProvider )fromsByProvider.clone();
                            childTablesByName.put("root",child);
                            return cb.and(getPredicates(cb, criteriaQuery, child, criterionList, childTablesByName));
                    } else if (criterion instanceof Query.IsNull c) {
                        return cb.isNull(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.IsNotNull c) {
                        return cb.isNotNull(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.IsEmpty c) {
                        return cb.isEmpty(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.IsNotEmpty c) {
                        return cb.isNotEmpty(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.Equals c) {
                        return cb.equal(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValue());
                    } else if (criterion instanceof Query.NotEquals c) {
                        return cb.notEqual(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValue());
                    } else if (criterion instanceof Query.EqualsProperty c) {
                        return cb.equal(fromsByProvider.getFullyQualifiedPath(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.NotEqualsProperty c) {
                        return cb.notEqual(fromsByProvider.getFullyQualifiedPath(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.LessThanEqualsProperty c) {
                        return cb.le(fromsByProvider.getFullyQualifiedPath(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.LessThanProperty c) {
                        return cb.lt(fromsByProvider.getFullyQualifiedPath(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.GreaterThanEqualsProperty c) {
                        return cb.ge(fromsByProvider.getFullyQualifiedPath(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.GreaterThanProperty c) {
                        return cb.gt(fromsByProvider.getFullyQualifiedPath(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.IdEquals c) {
                        return cb.equal(root_.get("id"), c.getValue());
                    } else if (criterion instanceof Query.GreaterThan c) {
                        return cb.gt(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.GreaterThanEquals c) {
                        return cb.ge(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.LessThan c) {
                        return cb.lt(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.LessThanEquals c) {
                        return cb.le(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeEquals c) {
                        return cb.equal(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), c.getValue());
                    } else if (criterion instanceof Query.SizeNotEquals c) {
                        return cb.notEqual(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), c.getValue());
                    } else if (criterion instanceof Query.SizeGreaterThan c) {
                        return cb.gt(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeGreaterThanEquals c) {
                        return cb.ge(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeLessThan c) {
                        return cb.lt(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeLessThanEquals c) {
                        return cb.le(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.Between c) {
                        if (c.getFrom() instanceof String && c.getTo() instanceof String) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (String) c.getFrom(), (String) c.getTo());
                        } else if (c.getFrom() instanceof Short && c.getTo() instanceof Short) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Short) c.getFrom(), (Short) c.getTo());
                        } else if (c.getFrom() instanceof Integer && c.getTo() instanceof Integer) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Integer) c.getFrom(), (Integer) c.getTo());
                        } else if (c.getFrom() instanceof Long && c.getTo() instanceof Long) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Long) c.getFrom(), (Long) c.getTo());
                        } else if (c.getFrom() instanceof Date && c.getTo() instanceof Date) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Date) c.getFrom(), (Date) c.getTo());
                        } else if (c.getFrom() instanceof Instant && c.getTo() instanceof Instant) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (Instant) c.getFrom(), (Instant) c.getTo());
                        } else if (c.getFrom() instanceof LocalDate && c.getTo() instanceof LocalDate) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (LocalDate) c.getFrom(), (LocalDate) c.getTo());
                        } else if (c.getFrom() instanceof LocalDateTime && c.getTo() instanceof LocalDateTime) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (LocalDateTime) c.getFrom(), (LocalDateTime) c.getTo());
                        } else if (c.getFrom() instanceof OffsetDateTime && c.getTo() instanceof OffsetDateTime) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (OffsetDateTime) c.getFrom(), (OffsetDateTime) c.getTo());
                        } else if (c.getFrom() instanceof ZonedDateTime && c.getTo() instanceof ZonedDateTime) {
                            return cb.between(fromsByProvider.getFullyQualifiedPath(c.getProperty()), (ZonedDateTime) c.getFrom(), (ZonedDateTime) c.getTo());
                        }
                    } else if (criterion instanceof Query.ILike c) {
                        return cb.ilike(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValue().toString());
                    } else if (criterion instanceof Query.RLike c) {
                        return cb.like(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getPattern(), '\\');
                    } else if (criterion instanceof Query.Like c) {
                        return cb.like(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValue().toString());
                    } else if (criterion instanceof Query.SizeEquals c) {
                        return cb.equal(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), c.getValue());
                    } else if (criterion instanceof Query.SizeGreaterThan c) {
                        return cb.gt(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeGreaterThanEquals c) {
                        return cb.ge(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeLessThan c) {
                        return cb.lt(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if (criterion instanceof Query.SizeLessThanEquals c) {
                        return cb.le(cb.size(fromsByProvider.getFullyQualifiedPath(c.getProperty())), (Number) c.getValue());
                    } else if(criterion instanceof Query.In || criterion instanceof Query.NotIn) {
                        var queryableCriteria = getQueryableCriteriaFromInCriteria(criterion);
                        if (Objects.nonNull(queryableCriteria)) {

                            var projection = findPropertyOrIdProjection(queryableCriteria);
                            var subProperty = findSubproperty(projection);
                            var path = getPathFromInCriterion(fromsByProvider, (Query.PropertyNameCriterion) criterion);
                            var in = findInPredicate(cb, projection, path, subProperty);
                            var subquery = criteriaQuery.subquery(getJavaTypeOfInClause((SqmInListPredicate) in));
                            var from = subquery.from(queryableCriteria.getPersistentEntity().getJavaClass());
                            var subCriteria = queryableCriteria.getCriteria();
                            var clonedProviderByName = (JpaFromProvider) fromsByProvider.clone();
                            clonedProviderByName.put("root", from);
                            var predicates = getPredicates(cb, criteriaQuery, from, subCriteria, clonedProviderByName);
                            subquery.select(clonedProviderByName.getFullyQualifiedPath(subProperty))
                                    .distinct(true)
                                    .where(cb.and(predicates));
                            CriteriaBuilder.In value = in.value(subquery);
                            if (criterion instanceof Query.In) {
                                return value;
                            }
                            return cb.not(value);
                        } else if (criterion instanceof Query.In c && !c.getValues().isEmpty()) {
                            return cb.in(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValues());
                        } else if (criterion instanceof Query.NotIn c) {
                            return cb.not(cb.in(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValue()));
                        }
                    } else if (criterion instanceof Query.Exists c) {
                        Subquery subquery = criteriaQuery.subquery(Integer.class);
                        PersistentEntity childPersistentEntity = c.getSubquery().getPersistentEntity();
                        Root subRoot = subquery.from(childPersistentEntity.getJavaClass());


                        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
                        newMap.put("root", subRoot);
                        var predicates = getPredicates(cb, criteriaQuery, subRoot, c.getSubquery().getCriteria(), newMap);

                        var existsPredicate = getExistsPredicate(cb, root_, childPersistentEntity, subRoot);
                        Predicate[] allPredicates = Stream.concat(
                                Arrays.stream(predicates),
                                Stream.of(existsPredicate)
                        ).toArray(Predicate[]::new);

                        subquery.select(cb.literal(1)).where(cb.and(allPredicates));
                        JpaPredicate exists = cb.exists(subquery);
                        return exists;
                    } else if (criterion instanceof Query.NotExists c) {
                        Subquery subquery = criteriaQuery.subquery(Integer.class);
                        PersistentEntity childPersistentEntity = c.getSubquery().getPersistentEntity();
                        Root subRoot = subquery.from(childPersistentEntity.getJavaClass());


                        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
                        newMap.put("root", subRoot);
                        var predicates = getPredicates(cb, criteriaQuery, subRoot, c.getSubquery().getCriteria(), newMap);

                        var existsPredicate = getExistsPredicate(cb, root_, childPersistentEntity, subRoot);
                        Predicate[] allPredicates = Stream.concat(
                                Arrays.stream(predicates),
                                Stream.of(existsPredicate)
                        ).toArray(Predicate[]::new);

                        subquery.select(cb.literal(1)).where(cb.and(allPredicates));
                        JpaPredicate exists = cb.exists(subquery);
                        return cb.not(exists);
                    } else if (criterion instanceof Query.SubqueryCriterion c) {
                        Subquery subquery = criteriaQuery.subquery(Number.class);
                        Root from = subquery.from(c.getValue().getPersistentEntity().getJavaClass());
                        List subCriteria = c.getValue().getCriteria();
                        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
                        newMap.put("root", from);
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, subCriteria, newMap);
                        if (c instanceof Query.GreaterThanEqualsAll sc) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.greaterThanOrEqualTo(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.GreaterThanAll sc) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.greaterThan(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.LessThanEqualsAll sc) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.lessThanOrEqualTo(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.LessThanAll sc) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.lessThan(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.EqualsAll sc) {
                            subquery.select(from.get(c.getProperty())).where(cb.and(predicates));
                            return cb.equal(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.GreaterThanEqualsSome sc) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.greaterThanOrEqualTo(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.GreaterThanSome sc) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.greaterThan(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.LessThanEqualsSome sc) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.lessThanOrEqualTo(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (c instanceof Query.LessThanSome sc) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.lessThan(fromsByProvider.getFullyQualifiedPath(sc.getProperty()), subquery);
                        } else if (criterion instanceof Query.NotIn sc
                                && Objects.nonNull(sc.getSubquery())
                                && !sc.getSubquery().getProjections().isEmpty()
                                && sc.getSubquery().getProjections().get(0) instanceof Query.PropertyProjection
                        ) {
                            Query.PropertyProjection projection = (Query.PropertyProjection) sc.getSubquery().getProjections().get(0);
                            boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                            subquery.select(from.get(projection.getPropertyName())).distinct(distinct).where(cb.and(predicates));
                            return cb.in(fromsByProvider.getFullyQualifiedPath(sc.getProperty())).value(subquery);
                        } else if (criterion instanceof Query.NotIn sc
                                && Objects.nonNull(sc.getSubquery())
                                && !sc.getSubquery().getProjections().isEmpty()
                                && sc.getSubquery().getProjections().get(0) instanceof Query.IdProjection
                        ) {
                            subquery.select(from).where(cb.and(predicates));
                            return cb.in(root_.get("id")).value(subquery);
                        }
                    }
                    throw new IllegalArgumentException("Unsupported criterion: " + criterion);
                }).filter(Objects::nonNull).toList();
        if (list.isEmpty()) {
            list = List.of(cb.equal(cb.literal(1),cb.literal(1)));
        }
        return list.toArray(new Predicate[0]);
    }

    private static Predicate getExistsPredicate(HibernateCriteriaBuilder cb, From root_, PersistentEntity childPersistentEntity, Root subRoot) {
        Association owner = childPersistentEntity
                .getAssociations()
                .stream()
                .filter(assoc -> assoc.getAssociatedEntity().getJavaClass().equals(root_.getJavaType()))
                .findFirst().orElseThrow();
        Predicate existsPredicate = cb.equal(subRoot.get(owner.getName()), root_);
        return existsPredicate;
    }

    @SuppressWarnings("rawtypes")
    private static JpaInPredicate findInPredicate(HibernateCriteriaBuilder cb, Object projection, Path path, String subProperty) {
        return projection instanceof Query.PropertyProjection ? cb.in(path) : cb.in(((SqmPath) path).get(subProperty));
    }

    private static String findSubproperty(Object projection) {
        return projection instanceof Query.PropertyProjection ? ((Query.PropertyProjection) projection).getPropertyName() :"id" ;
    }

    @SuppressWarnings("unchecked")
    private static Query.Projection findPropertyOrIdProjection(QueryableCriteria queryableCriteria) {
        return (Query.Projection ) queryableCriteria.getProjections()
                .stream().
                filter(projection1 -> projection1 instanceof Query.PropertyProjection || projection1 instanceof Query.IdProjection)
                .findFirst()
                .orElse(new Query.IdProjection());
    }

    @SuppressWarnings("rawtypes")
    private static QueryableCriteria getQueryableCriteriaFromInCriteria(Query.Criterion criterion) {
        return criterion instanceof Query.In  ? ((Query.In) criterion).getSubquery() : ((Query.NotIn) criterion).getSubquery();
    }

    @SuppressWarnings("rawtypes")
    private static Path getPathFromInCriterion(JpaFromProvider tablesByName, Query.PropertyNameCriterion criterion) {
        return tablesByName.getFullyQualifiedPath(criterion.getProperty());
    }


    @SuppressWarnings("rawtypes")
    private static Class getJavaTypeOfInClause(SqmInListPredicate predicate) {
        return Optional.ofNullable(predicate.getTestExpression()
                .getExpressible())
                .map(expressible ->  expressible.getExpressibleJavaType().getJavaTypeClass())
                .orElse(null);
    }
}
