/* Copyright (C) 2014 SpringSource
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
package org.grails.datastore.gorm.mongo.geo

import grails.mongodb.geo.GeoJSON
import grails.mongodb.geo.LineString
import grails.mongodb.geo.Point
import grails.mongodb.geo.Polygon
import grails.mongodb.geo.Shape
import groovy.transform.CompileStatic
import org.bson.Document
import org.grails.datastore.mapping.model.PersistentProperty
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.InvalidDataAccessResourceUsageException

/**
 *
 * Custom type for persisting the {@link Shape} type
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class ShapeType extends GeoJSONType<Shape>{

    static Map<String, Class> geoJsonTypeMap = [Polygon: Polygon, LineString: LineString, Point: Point]
    ShapeType() {
        super(Shape)
    }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, Shape value, Document nativeTarget) {
        if(value instanceof GeoJSON) {
            return super.writeInternal(property, key, value, nativeTarget)
        }
        else {
            throw new InvalidDataAccessResourceUsageException("Only GeoJSON shapes can be persisted using Shape inheritance.")
        }
    }

    @Override
    protected Shape readInternal(PersistentProperty property, String key, Document nativeSource) {
        def geoData = nativeSource.get(key)
        if(geoData && (geoData instanceof Map) ) {
            def geoType = geoData.get(GEO_TYPE)
            def coords = geoData.get(COORDINATES)
            if(geoType) {
                def cls = geoJsonTypeMap.get(geoType.toString())
                if(cls && coords) {
                    return cls.valueOf(coords)
                }
            }
            throw new DataAccessResourceFailureException("Invalid GeoJSON data returned: $nativeSource")
        }
    }

    @Override
    Shape createFromCoords(List coords) {
        // noop
    }
}
