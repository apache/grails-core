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
import org.hibernate.query.criteria.JpaFunction;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.predicate.SqmInListPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
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
                                            List criteriaList, JpaFromProvider fromsByProvider, PersistentEntity entity) {


        List<Predicate> list = criteriaList.stream().
                map((Object criterion) -> {
                    if (criterion instanceof Query.Junction junction) {
                        var criterionList = junction.getCriteria();
                        var predicates = (Predicate[])this.getPredicates(cb, criteriaQuery, root_, (List)criterionList, fromsByProvider, entity);
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
                    } else if (criterion instanceof Query.DistinctProjection) {
                        // this returns always true
                        return cb.conjunction();
                    } else if (criterion instanceof DetachedAssociationCriteria<?> c) {
                        Join child = root_.join(c.getAssociationPath(), JoinType.LEFT);
                        List<Query.Criterion> criterionList = c.getCriteria();
                        JpaFromProvider childTablesByName = (JpaFromProvider) fromsByProvider.clone();
                        childTablesByName.put("root", child);
                        return cb.and((Predicate[])this.getPredicates(cb, criteriaQuery, child, (List)criterionList, childTablesByName, entity));
                    } else if (criterion instanceof Query.PropertyCriterion pc) {
                        var fullyQualifiedPath = fromsByProvider.getFullyQualifiedPath(pc.getProperty());
                        if (criterion instanceof Query.Equals c) {
                            return cb.equal(fullyQualifiedPath, c.getValue());
                        } else if (criterion instanceof Query.NotEquals c) {
                            var notEqualToValue = cb.notEqual(fromsByProvider.getFullyQualifiedPath(c.getProperty()), c.getValue());
                            var isNull = cb.isNull(fullyQualifiedPath);
                            return cb.or(notEqualToValue, isNull);
                        } else if (criterion instanceof Query.IdEquals c) {
                            return cb.equal(root_.get("id"), c.getValue());
                        } else if (criterion instanceof Query.GreaterThan c) {
                            return cb.gt(fullyQualifiedPath, getNumericValue(c));
                        } else if (criterion instanceof Query.GreaterThanEquals c) {
                            return cb.ge(fullyQualifiedPath, getNumericValue(c));
                        } else if (criterion instanceof Query.LessThan c) {
                            return cb.lt(fullyQualifiedPath, getNumericValue(c));
                        } else if (criterion instanceof Query.LessThanEquals c) {
                            return cb.le(fullyQualifiedPath, getNumericValue(c));
                        } else if (criterion instanceof Query.SizeEquals c) {
                            return cb.equal(cb.size(fullyQualifiedPath), c.getValue());
                        } else if (criterion instanceof Query.SizeNotEquals c) {
                            return cb.notEqual(cb.size(fullyQualifiedPath), c.getValue());
                        } else if (criterion instanceof Query.SizeGreaterThan c) {
                            return cb.gt(cb.size(fullyQualifiedPath), getNumericValue(c));
                        } else if (criterion instanceof Query.SizeGreaterThanEquals c) {
                            return cb.ge(cb.size(fullyQualifiedPath), getNumericValue(c));
                        } else if (criterion instanceof Query.SizeLessThan c) {
                            return cb.lt(cb.size(fullyQualifiedPath), getNumericValue(c));
                        } else if (criterion instanceof Query.SizeLessThanEquals c) {
                            return cb.le(cb.size(fullyQualifiedPath), getNumericValue(c));
                        } else if (criterion instanceof Query.Between c) {
                            return cb.between(fullyQualifiedPath, (Comparable) c.getFrom(), (Comparable) c.getTo());
                        } else if (criterion instanceof Query.ILike c) {
                            return cb.ilike(fullyQualifiedPath, c.getValue().toString());
                        } else if (criterion instanceof Query.RLike c) {
                            String pattern = c.getPattern();
                            pattern = pattern.replaceAll("^/|/$", "");
                            return cb.equal(cb.function(
                                    GrailsRLikeFunctionContributor.RLIKE,           // The name we registered
                                    Boolean.class,     // Expected return type
                                    fullyQualifiedPath, // The property path
                                    cb.literal(pattern)), true);
                        } else if (criterion instanceof Query.Like c) {
                            return cb.like(fullyQualifiedPath, c.getValue().toString());
                        } else if (criterion instanceof Query.In c) {
                            var queryableCriteria = getQueryableCriteriaFromInCriteria((Query.Criterion)criterion);
                            if (Objects.nonNull(queryableCriteria)) {

                                CriteriaBuilder.In value = getQueryableCriteriaValue(cb, criteriaQuery, fromsByProvider, entity, (Query.PropertyNameCriterion)criterion, queryableCriteria);
                                return value;
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
                        } else if (criterion instanceof Query.NotIn c) {
                            var queryableCriteria = getQueryableCriteriaFromInCriteria((Query.Criterion)criterion);
                            if (Objects.nonNull(queryableCriteria)) {
                                CriteriaBuilder.In value = getQueryableCriteriaValue(cb, criteriaQuery, fromsByProvider, entity, (Query.PropertyNameCriterion)criterion, queryableCriteria);
                                return cb.not(value);
                            } else if (Objects.nonNull(c.getSubquery())
                                    && !c.getSubquery().getProjections().isEmpty()
                                    && c.getSubquery().getProjections().get(0) instanceof Query.PropertyProjection
                            ) {
                                Subquery subquery2 = criteriaQuery.subquery(Number.class);
                                Root from2 = subquery2.from(c.getValue().getPersistentEntity().getJavaClass());
                                List subCriteria2 = c.getValue().getCriteria();
                                Query.PropertyProjection projection = (Query.PropertyProjection) c.getSubquery().getProjections().get(0);
                                boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                                JpaFromProvider newMap2 = (JpaFromProvider) fromsByProvider.clone();
                                Predicate[] predicates2 = (Predicate[])this.getPredicates(cb, criteriaQuery, from2, (List)subCriteria2, newMap2, entity);
                                subquery2.select(from2.get(projection.getPropertyName())).distinct(distinct).where(cb.and(predicates2));
                                return cb.not(cb.in(fullyQualifiedPath).value(subquery2));
                            } else if ( Objects.nonNull(c.getSubquery())
                                    && !c.getSubquery().getProjections().isEmpty()
                                    && c.getSubquery().getProjections().get(0) instanceof Query.IdProjection
                            ) {
                                Subquery subquery2 = criteriaQuery.subquery(Number.class);
                                Root from2 = subquery2.from(c.getValue().getPersistentEntity().getJavaClass());
                                List subCriteria2 = c.getValue().getCriteria();
                                JpaFromProvider newMap2 = (JpaFromProvider) fromsByProvider.clone();
                                Predicate[] predicates2 = (Predicate[])this.getPredicates(cb, criteriaQuery, from2, (List)subCriteria2, newMap2, entity);
                                subquery2.select(from2).where(cb.and(predicates2));
                                return cb.not(cb.in(root_.get("id")).value(subquery2));
                            } else {
                                return cb.not(cb.in(fullyQualifiedPath, c.getValue()));
                            }

                        } else if (criterion instanceof Query.SubqueryCriterion c) {
                            Subquery subquery = criteriaQuery.subquery(Number.class);
                            Root from = subquery.from(c.getValue().getPersistentEntity().getJavaClass());
                            List subCriteria = c.getValue().getCriteria();
                            JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
                            newMap.put("root", from);
                            Predicate[] predicates = (Predicate[])this.getPredicates(cb, criteriaQuery, from, (List)subCriteria, newMap, entity);
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

                            }
                        }

                    } else if (criterion instanceof Query.IsNull c) {
                        return cb.isNull(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.IsNotNull c) {
                        return cb.isNotNull(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.IsEmpty c) {
                        return cb.isEmpty(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
                    } else if (criterion instanceof Query.IsNotEmpty c) {
                        return cb.isNotEmpty(fromsByProvider.getFullyQualifiedPath(c.getProperty()));
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
                    } else if (criterion instanceof Query.Exists c) {
                        Subquery subquery = criteriaQuery.subquery(Integer.class);
                        PersistentEntity childPersistentEntity = c.getSubquery().getPersistentEntity();
                        Root subRoot = subquery.from(childPersistentEntity.getJavaClass());


                        JpaFromProvider newMap = (JpaFromProvider) fromsByProvider.clone();
                        newMap.put("root", subRoot);
                        var predicates = (Predicate[])this.getPredicates(cb, criteriaQuery, subRoot, (List)c.getSubquery().getCriteria(), newMap, entity);

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
                        var predicates = (Predicate[])this.getPredicates(cb, criteriaQuery, subRoot, (List)c.getSubquery().getCriteria(), newMap, entity);

                        var existsPredicate = getExistsPredicate(cb, root_, childPersistentEntity, subRoot);
                        Predicate[] allPredicates = Stream.concat(
                                Arrays.stream(predicates),
                                Stream.of(existsPredicate)
                        ).toArray(Predicate[]::new);

                        subquery.select(cb.literal(1)).where(cb.and(allPredicates));
                        JpaPredicate exists = cb.exists(subquery);
                        return cb.not(exists);
                    }
                    throw new IllegalArgumentException("Unsupported criterion: " + criterion);
                }).filter(Objects::nonNull).toList();
        if (list.isEmpty()) {
            list = List.of(cb.equal(cb.literal(1),cb.literal(1)));
        }
        return list.toArray(new Predicate[0]);
    }

    private CriteriaBuilder.In getQueryableCriteriaValue(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, JpaFromProvider fromsByProvider, PersistentEntity entity, Query.PropertyNameCriterion criterion, QueryableCriteria queryableCriteria) {
        var projection = findPropertyOrIdProjection(queryableCriteria);
        var subProperty = findSubproperty(projection);
        var path = getPathFromInCriterion(fromsByProvider, criterion);
        var in = findInPredicate(cb, projection, path, subProperty);
        var subquery = criteriaQuery.subquery(getJavaTypeOfInClause((SqmInListPredicate) in));
        var from = subquery.from(queryableCriteria.getPersistentEntity().getJavaClass());
        var subCriteria = queryableCriteria.getCriteria();
        var clonedProviderByName = (JpaFromProvider) fromsByProvider.clone();
        clonedProviderByName.put("root", from);
        var predicates = this.getPredicates(cb, criteriaQuery, from, subCriteria, clonedProviderByName, entity);
        subquery.select(clonedProviderByName.getFullyQualifiedPath(subProperty))
                .distinct(true)
                .where(cb.and(predicates));
        CriteriaBuilder.In value = in.value(subquery);
        return value;
    }

    private Predicate getExistsPredicate(HibernateCriteriaBuilder cb, From root_, PersistentEntity childPersistentEntity, Root subRoot) {
        Association owner = childPersistentEntity
                .getAssociations()
                .stream()
                .filter(assoc -> assoc.getAssociatedEntity().getJavaClass().equals(root_.getJavaType()))
                .findFirst().orElseThrow();
        Predicate existsPredicate = cb.equal(subRoot.get(owner.getName()), root_);
        return existsPredicate;
    }

    @SuppressWarnings("rawtypes")
    private JpaInPredicate findInPredicate(HibernateCriteriaBuilder cb, Object projection, Path path, String subProperty) {
        return projection instanceof Query.PropertyProjection ? cb.in(path) : cb.in(((SqmPath) path).get(subProperty));
    }

    private String findSubproperty(Object projection) {
        return projection instanceof Query.PropertyProjection ? ((Query.PropertyProjection) projection).getPropertyName() :"id" ;
    }

    @SuppressWarnings("unchecked")
    private Query.Projection findPropertyOrIdProjection(QueryableCriteria queryableCriteria) {
        return (Query.Projection ) queryableCriteria.getProjections()
                .stream().
                filter(projection1 -> projection1 instanceof Query.PropertyProjection || projection1 instanceof Query.IdProjection)
                .findFirst()
                .orElse(new Query.IdProjection());
    }

    @SuppressWarnings("rawtypes")
    private QueryableCriteria getQueryableCriteriaFromInCriteria(Query.Criterion criterion) {
        return criterion instanceof Query.In  ? ((Query.In) criterion).getSubquery() : ((Query.NotIn) criterion).getSubquery();
    }

    @SuppressWarnings("rawtypes")
    private Path getPathFromInCriterion(JpaFromProvider tablesByName, Query.PropertyNameCriterion criterion) {
        return tablesByName.getFullyQualifiedPath(criterion.getProperty());
    }


    @SuppressWarnings("rawtypes")
    private Class getJavaTypeOfInClause(SqmInListPredicate predicate) {
        return Optional.ofNullable(predicate.getTestExpression()
                .getExpressible())
                .map(expressible ->  expressible.getExpressibleJavaType().getJavaTypeClass())
                .orElse(null);
    }

    private Number getNumericValue(Query.PropertyCriterion criterion) {
        Object value = criterion.getValue();
        if (value instanceof Number) {
            return (Number) value;
        }
        String operationName = criterion.getClass().getSimpleName();
        throw new ConfigurationException(
                String.format("Operation '%s' on property '%s' only accepts a numeric value, but received a %s",
                        operationName, criterion.getProperty(), (value == null ? "null" : value.getClass().getName())));
    }

}
