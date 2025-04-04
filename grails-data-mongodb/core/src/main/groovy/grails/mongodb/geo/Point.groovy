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
 * Represents a GeoJSON point for use in GeoJSON data models. See http://geojson.org/geojson-spec.html#point
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@EqualsAndHashCode
@CompileStatic
class Point extends Shape implements GeoJSON{
    /**
     * The x and y values that indicate the location of the point
     */
    final double x, y

    /**
     * Construct a point for the given x and y coordinates
     * @param x The x position
     * @param y The y position
     */
    Point(double x, double y) {
        this.x = x
        this.y = y
    }

    /**
     * @return An array representation of the point
     */
    double[] asArray() { [x,y] as double[] }

    /**
     * @return A list representation of the point
     */
    List<Double> asList() { [ x, y] }

    @Override
    String toString() { "[$x,$y]" }

    /**
     * Construct a point for the given x and y values
     *
     * @param x The x value
     * @param y The y value
     * @return The Point
     */
    static Point valueOf(Number x, Number y) {
        new Point(x.doubleValue(), y.doubleValue())
    }

    /**
     * Construct a point for the given coordinates supplied in the list
     *
     * @param coords A list containing 2 entries for the x and y positions
     * @return A Point
     */
    static Point valueOf(List<Number> coords) {
        if(coords.size() == 2) {
            def x = coords.get(0)
            def y = coords.get(1)
            if((x instanceof Number) && (y instanceof Number)) {
                return new Point(x.doubleValue(), y.doubleValue())
            }
        }
        throw new IllegalArgumentException("Invalid coordinates: $coords")
    }

    /**
     * Gets a point from the given list of coordinate lists
     *
     * @param coords The multi dimensional coordinate list
     * @param index The index of the point
     * @return A Point
     */
    static Point getPointAtIndex( List coords, int index ) {
        def coord = coords.get(index)
        if(coord instanceof Point) {
            return (Point)coord
        }
        else if(coord instanceof List) {
            return valueOf( (List<Number>) coord )
        }
        throw new IllegalArgumentException("Invalid coordinates: $coords")
    }

}
