package org.grails.orm.hibernate.query;

import org.grails.datastore.mapping.query.Query;

import java.util.Arrays;
import java.util.function.Predicate;

public class ProjectionPredicate
        implements Predicate<Query.Projection> {

    @Override
    public boolean test(Query.Projection projection) {
        return combinePredicates(projectionPredicates).test(projection);
    }

    private final Predicate<Query.Projection> idProjectionPredicate = projection -> projection instanceof Query.IdProjection;
    private final Predicate<Query.Projection> distinctProjectionPredicate = projection -> projection instanceof Query.DistinctProjection;
    private final Predicate<Query.Projection> countProjectionPredicate = projection -> projection instanceof Query.CountProjection;
    private final Predicate<Query.Projection> countDistinctProjection = projection -> projection instanceof Query.CountDistinctProjection;
    private final Predicate<Query.Projection> maxProjectionPredicate = projection -> projection instanceof Query.MaxProjection;
    private final Predicate<Query.Projection> minProjectionPredicate = projection -> projection instanceof Query.MinProjection;
    private final Predicate<Query.Projection> sumProjectionPredicate = projection -> projection instanceof Query.SumProjection;
    private final Predicate<Query.Projection> avgProjectionPredicate = projection -> projection instanceof Query.AvgProjection;
    private final Predicate<Query.Projection> propertyProjectionPredicate = projection -> projection instanceof Query.PropertyProjection;

    @SuppressWarnings("unchecked")
    Predicate<Query.Projection>[] projectionPredicates = new Predicate[] {
            idProjectionPredicate
            , propertyProjectionPredicate
            , countProjectionPredicate
            , countDistinctProjection
            , maxProjectionPredicate
            , minProjectionPredicate
            , sumProjectionPredicate
            , avgProjectionPredicate
            , distinctProjectionPredicate
    } ;

    @SafeVarargs
    private static <T> Predicate<T> combinePredicates(Predicate<T>... predicates) {
        return Arrays.stream(predicates)
                .reduce(Predicate::or)
                .orElse(x -> true);
    }


}
