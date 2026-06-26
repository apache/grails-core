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

import java.util.Arrays;
import java.util.function.Predicate;

import org.grails.datastore.mapping.query.Query;

public class ProjectionPredicate implements Predicate<Query.Projection> {

    private final Predicate<Query.Projection> idProjectionPredicate =
            projection -> projection instanceof Query.IdProjection;
    private final Predicate<Query.Projection> distinctProjectionPredicate =
            projection -> projection instanceof Query.DistinctProjection;
    private final Predicate<Query.Projection> countProjectionPredicate =
            projection -> projection instanceof Query.CountProjection;
    private final Predicate<Query.Projection> countDistinctProjection =
            projection -> projection instanceof Query.CountDistinctProjection;
    private final Predicate<Query.Projection> maxProjectionPredicate =
            projection -> projection instanceof Query.MaxProjection;
    private final Predicate<Query.Projection> minProjectionPredicate =
            projection -> projection instanceof Query.MinProjection;
    private final Predicate<Query.Projection> sumProjectionPredicate =
            projection -> projection instanceof Query.SumProjection;
    private final Predicate<Query.Projection> avgProjectionPredicate =
            projection -> projection instanceof Query.AvgProjection;
    private final Predicate<Query.Projection> propertyProjectionPredicate =
            projection -> projection instanceof Query.PropertyProjection;

    @SuppressWarnings("unchecked")
    Predicate<Query.Projection>[] projectionPredicates = new Predicate[] {
        idProjectionPredicate,
        propertyProjectionPredicate,
        countProjectionPredicate,
        countDistinctProjection,
        maxProjectionPredicate,
        minProjectionPredicate,
        sumProjectionPredicate,
        avgProjectionPredicate,
        distinctProjectionPredicate
    };

    @SafeVarargs
    private static <T> Predicate<T> combinePredicates(Predicate<T>... predicates) {
        return Arrays.stream(predicates).reduce(Predicate::or).orElse(x -> true);
    }

    @Override
    public boolean test(Query.Projection projection) {
        return combinePredicates(projectionPredicates).test(projection);
    }
}
