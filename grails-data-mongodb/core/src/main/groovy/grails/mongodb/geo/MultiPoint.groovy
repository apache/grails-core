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
 * Represents a GeoJSON MultiPoint. See http://geojson.org/geojson-spec.html#multipoint
 *
 * Note: Requires MongoDB 2.6 or above
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@EqualsAndHashCode
class MultiPoint extends Shape implements GeoJSON{
    final List<Point> positions

    MultiPoint(Point... positions) {
        this.positions = Arrays.asList(positions)
    }

    MultiPoint(Point a, Point b) {
        this.positions = Arrays.asList(a, b)
    }

    @Override
    List<List<Double>> asList() {
        return positions.collect() { Point p -> p.asList() }
    }

    @Override
    String toString() {
        positions.toString()
    }

    public static MultiPoint valueOf(List coords) {
        List<Point> points = (List<Point>) coords.collect() {
            if(it instanceof Point) {
                return it
            }
            else if(it instanceof List) {
                return Point.valueOf((List<Number>)it)
            }
            throw new IllegalArgumentException("Invalid coordinates: $coords")
        }

        return new MultiPoint(points as Point[])
    }
}
