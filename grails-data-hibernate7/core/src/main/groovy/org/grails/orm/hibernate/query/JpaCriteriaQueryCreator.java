package org.grails.orm.hibernate.query;

import grails.gorm.DetachedCriteria;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
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
    private final DetachedCriteria<?> detachedCriteria;


    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections
            , HibernateCriteriaBuilder criteriaBuilder
            , PersistentEntity entity
            , DetachedCriteria<?> detachedCriteria
    ) {
        this.projections = projections;
        this.criteriaBuilder = criteriaBuilder;
        this.entity = entity;
        this.detachedCriteria = detachedCriteria;
    }

    public JpaCriteriaQuery<?> createQuery() {

        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = cq.from(javaClass);
        var tablesByName = new JpaFromProvider(detachedCriteria,cq,root);


        assignProjections(projectionList, cq, tablesByName);

        List<Query.GroupPropertyProjection> groupProjections = collectGroupProjections();
        assignGroupBy(groupProjections, cq, tablesByName);

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
                .toList().size() > 1 ?  criteriaBuilder.createTupleQuery() : criteriaBuilder.createQuery(Object.class);
        projections.stream()
                .filter( it -> it instanceof Query.DistinctProjection || it instanceof Query.DistinctPropertyProjection)
                .findFirst()
                .ifPresent(projection -> cq.distinct(true));
        return cq;
    }

    private <T> void assignProjections(List<Query.Projection> projections, CriteriaQuery<T> cq, JpaFromProvider tablesByName) {
        var projectionExpressions = projections
                .stream()
                .map(projectionToJpaExpression(tablesByName))
                .filter(Objects::nonNull)
                .toList();
        if (projectionExpressions.size() == 1) {
            JpaExpression<?> jpaExpression = projectionExpressions.get(0);
            cq.select((Selection<? extends T>) jpaExpression);
        } else if (projectionExpressions.size() > 1){
            var selectionArray = projectionExpressions.toArray(new Selection<?>[0]);
            CriteriaQuery<Tuple> tupleCriteriaQuery = (CriteriaQuery<Tuple>) cq;
            tupleCriteriaQuery.select(criteriaBuilder.tuple(selectionArray));
        } else {
            Path<?> root = tablesByName.getFullyQualifiedPath("root");
            cq.select((Selection<? extends T>) root);
        }
    }

    private void assignGroupBy(List<Query.GroupPropertyProjection> groupProjections, CriteriaQuery<?> cq, JpaFromProvider tablesByName) {
        if (!groupProjections.isEmpty()) {
            var groupByPaths = groupProjections
                    .stream()
                    .map(groupPropertyProjection -> {
                        String propertyName = groupPropertyProjection.getPropertyName();
                        return tablesByName.getFullyQualifiedPath(propertyName);
                    })
                    .filter(Objects::nonNull)
                    .toArray(Path<?>[]::new);
            cq.groupBy(groupByPaths);
        }
    }

    private void assignOrderBy(CriteriaQuery<?> cq, JpaFromProvider tablesByName) {
        List<Query.Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            var jpaOrders = orders.stream()
                    .map(order -> {
                        Path<?> expression = tablesByName.getFullyQualifiedPath(order.getProperty());
                        if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                            return order.getDirection().equals(Query.Order.Direction.ASC)
                                    ? criteriaBuilder.asc(criteriaBuilder.lower((Expression<String>) expression))
                                    : criteriaBuilder.desc(criteriaBuilder.lower((Expression<String>) expression));
                        } else {
                            return order.getDirection().equals(Query.Order.Direction.ASC)
                                    ? criteriaBuilder.asc(expression)
                                    : criteriaBuilder.desc(expression);
                        }
                    })
                    .toArray(Order[]::new);
            cq.orderBy(jpaOrders);
        }
    }

    private void assignCriteria(CriteriaQuery<?> cq, From<?, ?> root, JpaFromProvider tablesByName, PersistentEntity entity) {
        List<Query.Criterion> criteriaList = detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            Predicate[] predicates = new PredicateGenerator().getPredicates(criteriaBuilder, cq, root, criteriaList, tablesByName, entity);
            cq.where(criteriaBuilder.and(predicates));
        }
    }

    private Function<Query.Projection, JpaExpression<?>> projectionToJpaExpression(
            JpaFromProvider tablesByName) {
        return projection -> {
            if (projection instanceof Query.CountProjection) {
                return criteriaBuilder.count(tablesByName.getFullyQualifiedPath("root"));
            } else if (projection instanceof Query.CountDistinctProjection countDistinctProjection) {
                var propertyName = countDistinctProjection.getPropertyName();
                return criteriaBuilder.countDistinct(tablesByName.getFullyQualifiedPath("root." + propertyName));
            } else if (projection instanceof Query.IdProjection) {
                return (JpaExpression<?>) tablesByName.getFullyQualifiedPath("root.id");
            } else if (projection instanceof Query.DistinctProjection) {
                return null;
            } else {
                var propertyName = ((Query.PropertyProjection) projection).getPropertyName();
                Path<?> path = tablesByName.getFullyQualifiedPath(propertyName);
                if (projection instanceof Query.MaxProjection) {
                    return criteriaBuilder.max((Expression<? extends Number>)path);
                } else if (projection instanceof Query.MinProjection) {
                    return criteriaBuilder.min((Expression<? extends Number>)path);
                } else if (projection instanceof Query.AvgProjection) {
                    return criteriaBuilder.avg((Expression<? extends Number>)path);
                } else if (projection instanceof Query.SumProjection) {
                    return criteriaBuilder.sum((Expression<? extends Number>)path);
                } else { // keep this last!!!
                    return (JpaExpression<?>)path;
                }
            }
        };
    }

    private List<Query.GroupPropertyProjection> collectGroupProjections() {
        return projections.getProjectionList()
                .stream()
                .filter(Query.GroupPropertyProjection.class::isInstance)
                .map(Query.GroupPropertyProjection.class::cast)
                .toList();
    }

}
