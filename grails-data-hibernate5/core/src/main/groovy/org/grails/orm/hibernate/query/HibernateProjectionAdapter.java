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

import org.grails.datastore.mapping.query.Query;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts Grails datastore API to Hibernate projections.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class HibernateProjectionAdapter {

    private static final Map<Class<?>, ProjectionAdapter> ADAPTER_MAP = new HashMap<>();
    private Query.Projection projection;

    static {
        ADAPTER_MAP.put(Query.AvgProjection.class, gormProjection -> {
            Query.AvgProjection avg = (Query.AvgProjection) gormProjection;
            return Projections.avg(avg.getPropertyName());
        });
        ADAPTER_MAP.put(Query.IdProjection.class, gormProjection -> Projections.id());
        ADAPTER_MAP.put(Query.SumProjection.class, gormProjection -> {
            Query.SumProjection avg = (Query.SumProjection) gormProjection;
            return Projections.sum(avg.getPropertyName());
        });
        ADAPTER_MAP.put(Query.DistinctPropertyProjection.class, gormProjection -> {
            Query.DistinctPropertyProjection avg = (Query.DistinctPropertyProjection) gormProjection;
            return Projections.distinct(Projections.property(avg.getPropertyName()));
        });
        ADAPTER_MAP.put(Query.PropertyProjection.class, gormProjection -> {
            Query.PropertyProjection avg = (Query.PropertyProjection) gormProjection;
            return Projections.property(avg.getPropertyName());
        });
        ADAPTER_MAP.put(Query.CountProjection.class, gormProjection -> Projections.rowCount());
        ADAPTER_MAP.put(Query.CountDistinctProjection.class, gormProjection -> {
            Query.CountDistinctProjection cd = (Query.CountDistinctProjection) gormProjection;
            return Projections.countDistinct(cd.getPropertyName());
        });
        ADAPTER_MAP.put(Query.GroupPropertyProjection.class, gormProjection -> {
            Query.GroupPropertyProjection cd = (Query.GroupPropertyProjection) gormProjection;
            return Projections.groupProperty(cd.getPropertyName());
        });
        ADAPTER_MAP.put(Query.MaxProjection.class, gormProjection -> {
            Query.MaxProjection cd = (Query.MaxProjection) gormProjection;
            return Projections.max(cd.getPropertyName());
        });
        ADAPTER_MAP.put(Query.MinProjection.class, gormProjection -> {
            Query.MinProjection cd = (Query.MinProjection) gormProjection;
            return Projections.min(cd.getPropertyName());
        });
    }

    public HibernateProjectionAdapter(Query.Projection projection) {
        this.projection = projection;
    }

    public Projection toHibernateProjection() {
        ProjectionAdapter projectionAdapter = ADAPTER_MAP.get(projection.getClass());
        if (projectionAdapter == null) {
            throw new UnsupportedOperationException("Unsupported projection used: " + projection.getClass().getName());
        }
        return projectionAdapter.toHibernateProjection(projection);
    }

    private interface ProjectionAdapter {

        Projection toHibernateProjection(Query.Projection gormProjection);
    }
}
