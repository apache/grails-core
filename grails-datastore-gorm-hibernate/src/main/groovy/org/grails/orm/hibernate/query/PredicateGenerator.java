package org.grails.orm.hibernate.query;

import groovy.util.logging.Slf4j;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@Slf4j
public class PredicateGenerator {
    private static final Logger log = LoggerFactory.getLogger(PredicateGenerator.class);

    public static Predicate[] getPredicates(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, Root root_ , List<Query.Criterion> criteriaList) {


        return criteriaList.stream().
                map(criterion -> {
                    if (criterion instanceof Query.Disjunction) {
                        List<Query.Criterion> criterionList = ((Query.Disjunction) criterion).getCriteria();
                        return cb.or(getPredicates(cb,criteriaQuery,root_, criterionList));
                    } else if (criterion instanceof Query.Conjunction) {
                        List<Query.Criterion> criterionList = ((Query.Conjunction) criterion).getCriteria();
                        return cb.and(getPredicates(cb,criteriaQuery,root_, criterionList));
                    } else if (criterion instanceof Query.Negation) {
                        List<Query.Criterion> criterionList = ((Query.Negation) criterion).getCriteria();
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, root_, criterionList);
                        if (predicates.length != 1) {
                            log.error("Must have a single predicate behind a not");
                            throw new RuntimeException("Must have a single predicate behind a not");
                        }
                        return cb.not(predicates[0]);
                    } else if (criterion instanceof Query.IsNotNull c) {
                        return cb.isNotNull(root_.get(c.getProperty()));
                    } else if (criterion instanceof Query.IsEmpty c) {
                        return cb.isEmpty(root_.get(c.getProperty()));
                    } else if (criterion instanceof Query.Equals c ) {
                        return cb.equal(root_.get(c.getProperty()),c.getValue());
                    } else if (criterion instanceof Query.IdEquals c ) {
                        return cb.equal(root_.get("id"),c.getValue());
                    } else if (criterion instanceof Query.GreaterThan c ) {
                        return cb.gt(root_.get(c.getProperty()),(Number)c.getValue());
                    } else if (criterion instanceof Query.GreaterThanEquals c ) {
                        return cb.ge(root_.get(c.getProperty()),(Number)c.getValue());
                    } else if (criterion instanceof Query.LessThan c ) {
                        return cb.lt(root_.get(c.getProperty()),(Number)c.getValue());
                    } else if (criterion instanceof Query.LessThanEquals c ) {
                        return cb.le(root_.get(c.getProperty()),(Number)c.getValue());
                    } else if (criterion instanceof Query.ILike c ) {
                        return cb.ilike(root_.get(c.getProperty()),c.getValue().toString());
                    } else if (criterion instanceof Query.RLike c ) {
                        return cb.like(root_.get(c.getProperty()),c.getPattern(), '\\');
                    } else if (criterion instanceof Query.Like c ) {
                        return cb.like(root_.get(c.getProperty()),c.getValue().toString());
                    } else if (criterion instanceof Query.In c
                            && Objects.nonNull(c.getSubquery())
                            && c.getSubquery().getProjections().size() == 1
                            && c.getSubquery().getProjections().get(0) instanceof Query.PropertyProjection
                    ) {
                        Query.PropertyProjection projection = (Query.PropertyProjection) c.getSubquery().getProjections().get(0);
                        boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                        Subquery subquery = criteriaQuery.subquery(Long.class);
                        Root from = subquery.from(c.getSubquery().getPersistentEntity().getJavaClass());
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getSubquery().getCriteria());
                        subquery.select(from.get(projection.getPropertyName())).distinct(distinct).where(cb.and(predicates));
                        return cb.in(root_.get(c.getProperty())).value(subquery);
                    } else if (criterion instanceof Query.In c
                            && !c.getValues().isEmpty()
                    ) {
                        return cb.in(root_.get(c.getProperty()), c.getValues());
                    }
                    return null;
                }).filter(Objects::nonNull).toList().toArray(new Predicate[0]);
    }
}
