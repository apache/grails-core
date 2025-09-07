package org.grails.orm.hibernate.query;

import grails.gorm.DetachedCriteria;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaExpression;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class JpaCriteriaQueryCreator {

    private final Query.ProjectionList projections;
    private final HibernateCriteriaBuilder criteriaBuilder;
    private final PersistentEntity entity;
    private final DetachedCriteria detachedCriteria;

    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections
            , HibernateCriteriaBuilder criteriaBuilder
            , PersistentEntity entity
            , DetachedCriteria detachedCriteria
    ) {
        this.projections = projections;
        this.criteriaBuilder = criteriaBuilder;
        this.entity = entity;
        this.detachedCriteria = detachedCriteria;
    }

    public JpaCriteriaQuery<?> createQuery() {

        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        From root = cq.from(entity.getJavaClass());
        var tablesByName = new JpaFromProvider(detachedCriteria,cq,root);


        assignProjections(projectionList, cq, tablesByName);

        List<Query.GroupPropertyProjection> groupProjections = collectGroupProjections();
        assignGroupBy(groupProjections, root, cq, tablesByName);

        assignOrderBy(cq, tablesByName);
        assignCriteria(cq, root,tablesByName,entity);
        return cq;
    }

    private List<Query.Projection> collectProjections() {
        return projections.getProjectionList()
                .stream()
                .filter(new ProjectionPredicate())
                .toList();
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Query.Projection> projections) {
        var cq = projections.stream()
                .filter( it -> !(it instanceof Query.DistinctProjection || it instanceof Query.DistinctPropertyProjection))
                .toList().size() > 1 ?  criteriaBuilder.createQuery(Object[].class) : criteriaBuilder.createQuery(Object.class);
        projections.stream()
                .filter( it -> it instanceof Query.DistinctProjection || it instanceof Query.DistinctPropertyProjection)
                .findFirst()
                .ifPresent(projection -> {
                    cq.distinct(true);
                });
        return cq;
    }

    private void assignProjections(List<Query.Projection> projections, CriteriaQuery cq, JpaFromProvider tablesByName) {
        var projectionExpressions = projections
                .stream()
                .map(projectionToJpaExpression(tablesByName))
                .filter(Objects::nonNull)
                .map(Expression.class::cast)
                .toList();
        if (projectionExpressions.size() == 1) {
            cq.select(projectionExpressions.get(0));
        } else if (projectionExpressions.size() > 1){
            cq.multiselect(projectionExpressions);
        } else {
            cq.select(tablesByName.getFullyQualifiedPath("root"));
        }
    }

    private void assignGroupBy(List<Query.GroupPropertyProjection> groupProjections, From root, CriteriaQuery cq, JpaFromProvider tablesByName) {
        if (!groupProjections.isEmpty()) {
            List<Expression> groupByPaths = groupProjections
                    .stream()
                    .map(groupPropertyProjection -> {
                        String propertyName = groupPropertyProjection.getPropertyName();
                        return tablesByName.getFullyQualifiedPath(propertyName);
                    })
                    .map(Expression.class::cast)
                    .toList();
            cq.groupBy(groupByPaths);
        }
    }

    private void assignOrderBy(CriteriaQuery cq, JpaFromProvider tablesByName) {
        List<Query.Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            cq.orderBy(orders
                    .stream()
                    .map(order -> {
                        Path expression = tablesByName.getFullyQualifiedPath(order.getProperty());
                        if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                            if (order.getDirection().equals(Query.Order.Direction.ASC)) {
                                return criteriaBuilder.asc(criteriaBuilder.lower(expression));
                            }  else {
                                return criteriaBuilder.desc(criteriaBuilder.lower(expression));
                            }
                        } else {
                            if (order.getDirection().equals(Query.Order.Direction.ASC)) {
                                return criteriaBuilder.asc(expression);
                            }  else {
                                return criteriaBuilder.desc(expression);
                            }
                        }

                    })
                    .toList()
            );
        }
    }

    private void assignCriteria(CriteriaQuery cq , From root, JpaFromProvider tablesByName, PersistentEntity entity) {
        List<Query.Criterion>  criteriaList =detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            jakarta.persistence.criteria.Predicate[] predicates = PredicateGenerator.getPredicates(criteriaBuilder, cq, root, criteriaList, tablesByName,entity);
            cq.where(criteriaBuilder.and(predicates));
        }
    }

    private Function<Query.Projection, JpaExpression> projectionToJpaExpression(
            JpaFromProvider tablesByName) {
        return projection -> {
            if (projection instanceof Query.CountProjection) {
                return criteriaBuilder.count(tablesByName.getFullyQualifiedPath("root"));
            } else if (projection instanceof Query.CountDistinctProjection countDistinctProjection) {
                var propertyName = countDistinctProjection.getPropertyName();
                return criteriaBuilder.countDistinct(tablesByName.getFullyQualifiedPath("root." + propertyName));
            } else if (projection instanceof Query.IdProjection) {
                return (JpaExpression) tablesByName.getFullyQualifiedPath("root.id");
            } else if (projection instanceof Query.DistinctProjection) {
                return null;
            } else {
                var propertyName = ((Query.PropertyProjection) projection).getPropertyName();
                Path path = tablesByName.getFullyQualifiedPath(propertyName);
                if (projection instanceof Query.MaxProjection) {
                    return criteriaBuilder.max(path);
                } else if (projection instanceof Query.MinProjection) {
                    return criteriaBuilder.min(path);
                } else if (projection instanceof Query.AvgProjection) {
                    return criteriaBuilder.avg(path);
                } else if (projection instanceof Query.SumProjection) {
                    return criteriaBuilder.sum(path);
                } else { // keep this last!!!
                    return (JpaExpression)path;
                }
            }
        };
    }

    private List<Query.GroupPropertyProjection> collectGroupProjections() {
        List<Query.GroupPropertyProjection> groupProjections = projections.getProjectionList()
                .stream()
                .filter(Query.GroupPropertyProjection.class::isInstance)
                .map(Query.GroupPropertyProjection.class::cast)
                .toList();
        return groupProjections;
    }

}
