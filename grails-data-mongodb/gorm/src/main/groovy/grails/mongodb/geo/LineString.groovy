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
 * See http://geojson.org/geojson-spec.html#linestring
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
@EqualsAndHashCode
class LineString extends Shape implements GeoJSON{
    /**
     * The points that constitute the LineString
     */
    final List<Point>  coordinates

    /**
     * Constructs a LineString for the given {@link Point} instances
     *
     * @param points The {@link Point} instances. Must be at least 2 points.
     */
    LineString(Point...points) {
        if(points.size() < 2)
            throw new IllegalArgumentException("At least 2 points required for a LineString")
        this.coordinates = points.toList()
    }

    /**
     * Converts the line string to a multi dimensional coordinate list.
     * Example: [ [1.0d, 4.0d], [8.0d, 4.0d] ]
     * @return
     */
    @Override
    List<List<Double>> asList() {
        coordinates.collect() { Point p -> p.asList()}
    }

    @Override
    String toString() {
        coordinates.toString()
    }

    /**
     * Constructs a LineString for the given coordinates
     * @param coords The coordinates, which should be a list of {@link Point} instances or lists containing x and y values
     * @return A LineString
     */
    public static LineString valueOf(List coords) {
        if(coords.size() < 2) throw new IllegalArgumentException("Coordinates should contain at least 2 entries for a LineString")

        List<Point> points = (List<Point>) coords.collect() {
            if(it instanceof Point) {
                return it
            }
            else if(it instanceof List) {
                return Point.valueOf((List<Number>)it)
            }
            throw new IllegalArgumentException("Invalid coordinates: $coords")
        }

        return new LineString(points as Point[])
    }
}
