/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query;

import java.util.HashMap;
import java.util.Map;

import org.grails.datastore.mapping.query.Query;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;

/**
 * Adapts Grails datastore API to Hibernate projections.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class HibernateProjectionAdapter {
    private Query.Projection projection;
    private static Map<Class<?>, ProjectionAdapter> adapterMap = new HashMap<>();

    static {
        adapterMap.put(Query.AvgProjection.class, gormProjection -> {
            Query.AvgProjection avg = (Query.AvgProjection) gormProjection;
            return Projections.avg(avg.getPropertyName());
        });
        adapterMap.put(Query.IdProjection.class, gormProjection -> Projections.id());
        adapterMap.put(Query.SumProjection.class, gormProjection -> {
            Query.SumProjection avg = (Query.SumProjection) gormProjection;
            return Projections.sum(avg.getPropertyName());
        });
        adapterMap.put(Query.DistinctPropertyProjection.class, gormProjection -> {
            Query.DistinctPropertyProjection avg = (Query.DistinctPropertyProjection) gormProjection;
            return Projections.distinct(Projections.property(avg.getPropertyName()));
        });
        adapterMap.put(Query.PropertyProjection.class, gormProjection -> {
            Query.PropertyProjection avg = (Query.PropertyProjection) gormProjection;
            return Projections.property(avg.getPropertyName());
        });
        adapterMap.put(Query.CountProjection.class, gormProjection -> Projections.rowCount());
        adapterMap.put(Query.CountDistinctProjection.class, gormProjection -> {
            Query.CountDistinctProjection cd = (Query.CountDistinctProjection) gormProjection;
            return Projections.countDistinct(cd.getPropertyName());
        });
        adapterMap.put(Query.GroupPropertyProjection.class, gormProjection -> {
            Query.GroupPropertyProjection cd = (Query.GroupPropertyProjection) gormProjection;
            return Projections.groupProperty(cd.getPropertyName());
        });
        adapterMap.put(Query.MaxProjection.class, gormProjection -> {
            Query.MaxProjection cd = (Query.MaxProjection) gormProjection;
            return Projections.max(cd.getPropertyName());
        });
        adapterMap.put(Query.MinProjection.class, gormProjection -> {
            Query.MinProjection cd = (Query.MinProjection) gormProjection;
            return Projections.min(cd.getPropertyName());
        });
    }

    public HibernateProjectionAdapter(Query.Projection projection) {
        this.projection = projection;
    }

    public Projection toHibernateProjection() {
        ProjectionAdapter projectionAdapter = adapterMap.get(projection.getClass());
        if(projectionAdapter == null) throw new UnsupportedOperationException("Unsupported projection used: " + projection.getClass().getName());
        return projectionAdapter.toHibernateProjection(projection);
    }

    private interface ProjectionAdapter {
        Projection toHibernateProjection(Query.Projection gormProjection);
    }
}
