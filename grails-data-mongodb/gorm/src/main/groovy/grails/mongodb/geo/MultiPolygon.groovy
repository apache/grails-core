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
package grails.mongodb.geo

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * Represents a GeoJSON MultiPolygon. See http://geojson.org/geojson-spec.html#multipolygon
 *
 * Note: Requires MongoDB 2.6 or above
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@EqualsAndHashCode
class MultiPolygon extends Shape implements GeoJSON {
    final List<Polygon> polygons

    MultiPolygon(Polygon...polygons) {
        this.polygons = Arrays.asList(polygons)
    }
    MultiPolygon(List<Polygon> polygons) {
        this.polygons = polygons
    }

    @Override
    List<List<List<List<Double>>>> asList() {
        return polygons.collect() { Polygon p -> p.asList() }
    }

    static MultiPolygon valueOf(List coords) {
        List<Polygon> polygons = (List<Polygon>) coords.collect() {
            if(it instanceof Polygon) {
                return it
            }
            else if(it instanceof List) {
                return Polygon.valueOf((List)it)
            }
            throw new IllegalArgumentException("Invalid coordinates: $coords")
        }

        return new MultiPolygon(polygons as Polygon[])
    }
}
