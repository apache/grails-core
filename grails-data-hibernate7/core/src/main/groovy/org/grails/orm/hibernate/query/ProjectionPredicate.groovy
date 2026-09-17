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
package org.grails.orm.hibernate.query

import java.util.function.Predicate

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.query.Query

@CompileStatic
class ProjectionPredicate implements Predicate<Query.Projection> {

    private final Predicate<Query.Projection> idProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.IdProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> distinctProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.DistinctProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> countProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.CountProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> countDistinctProjection =
            { Query.Projection projection -> projection instanceof Query.CountDistinctProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> maxProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.MaxProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> minProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.MinProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> sumProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.SumProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> avgProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.AvgProjection } as Predicate<Query.Projection>
    private final Predicate<Query.Projection> propertyProjectionPredicate =
            { Query.Projection projection -> projection instanceof Query.PropertyProjection } as Predicate<Query.Projection>

    @SuppressWarnings('unchecked')
    Predicate<Query.Projection>[] projectionPredicates = [
        idProjectionPredicate,
        propertyProjectionPredicate,
        countProjectionPredicate,
        countDistinctProjection,
        maxProjectionPredicate,
        minProjectionPredicate,
        sumProjectionPredicate,
        avgProjectionPredicate,
        distinctProjectionPredicate
    ] as Predicate[]

    @SafeVarargs
    private static <T> Predicate<T> combinePredicates(Predicate<T>... predicates) {
        Predicate<T> result = null
        for (Predicate<T> predicate : predicates) {
            result = result == null ? predicate : result.or(predicate)
        }
        return result != null ? result : ({ T x -> true } as Predicate<T>)
    }

    @Override
    boolean test(Query.Projection projection) {
        return combinePredicates(projectionPredicates).test(projection)
    }

}
