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
import org.springframework.util.Assert

/**
 * Represents a GeoJSON polygon for use in Geo data models.
 * See http://geojson.org/geojson-spec.html#polygon
 */
@CompileStatic
@EqualsAndHashCode
class Polygon extends Shape implements GeoJSON{

    /**
     * The {@link Point} instances that constitute the Polygon
     */
    final List<List<Point>> points

    /**
     * Constructs a Polygon for the given {@link Point} instances
     *
     * @param x The x {@link Point}
     * @param y The y {@link Point}
     * @param z The z {@link Point}
     * @param others The remaining {@link Point} instances
     */
    Polygon(Point x, Point y, Point z, Point...others) {
        Assert.notNull(x, "Point x is required")
        Assert.notNull(y, "Point y is required")
        Assert.notNull(z, "Point z is required")
        Assert.notNull(others, "Point others is required")

        List<Point> list = []
        list.addAll Arrays.asList(x, y, z)
        list.addAll others
        this.points = [list]
    }

    private Polygon(List<List<Point>> points){
        this.points = points
    }

    /**
     * Converts the Polygon to a multi-dimensional list of coordinates.
     * Example: [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0] ]
     *
     * @return The list
     */
    public List<List<List<Double>>> asList() {
         points.collect() { List<Point> ring ->
            ring.collect { Point p -> 
                p.asList()
            }
        }
    }

    @Override
    String toString() {
        points.toString()
    }
/**
     * The inverse of {@link Polygon#asList()}, constructs a Polygon from a coordinate list
     *
     * @param coords The coordinate list
     * @return A Polygon
     */
    static Polygon valueOf(List coords) {
        Assert.notNull(coords, "Argument coords cannot be null")

        

        /*
         * Search for list type - it could be 
         * (1) List<Point>  - a single ring polygon
         * (2) List<List<Number>> - a single ring with list as long/lat/alt
         * (3) List<List<Point> - a multi-ring polygon
         * (4) List<List<List<Double> - a multi-ring polygon with list as long/lat/alt
         */
         try 
         {
            if(coords[0] instanceof Point){
                return new Polygon( [fromSingleCoordsList(coords)] ) // case (1) above
             }
             else if(coords[0] instanceof List )
             {
                if( ((List)coords[0])[0] instanceof Number) {
                    return new Polygon( [fromSingleCoordsList(coords)] ) // case (2) above
                }
                else if( ((List)coords[0])[0] instanceof Point){
                    return new Polygon( coords.collect { poly_ring ->
                        // each is a List<Point>
                        return fromSingleCoordsList((List<Point>)poly_ring)
                    }) // case (3) above
                }
                else if( ((List)coords[0])[0] instanceof List && ((List)((List)coords[0])[0])[0] instanceof Number ){
                    return new Polygon( coords.collect { poly_ring ->
                        // each is a List<Point>
                        return fromSingleCoordsList((List<List<Number>>)poly_ring)
                    } ) // case (4) above
                }
                else {
                    throw new IllegalArgumentException("Coordinate list must be Points or number-lists")
                }
             }
             else {
                throw new IllegalArgumentException("Coordinate list must be Points or number-lists")
             }
         }
         catch(IndexOutOfBoundsException ioobe){
            throw new IllegalArgumentException("Coordinate lists cannot be empty")
         }
         
    }

    /**
     *  A single ring.  This could be a list of Point objects or a List points as number lists.
     *   E.g. List<Point> or List<List<Number>> 
     */
    private static List<Point> fromSingleCoordsList(List coords) {
        Assert.notNull(coords, "Argument coords cannot be null")

        if(coords.size() < 4) throw new IllegalArgumentException("Coordinates should contain at least 4 entries for a Polygon")

        return coords.collect {
            if(it instanceof Point) {
                return (Point)it
            }
            else if(it instanceof List) {
                return Point.valueOf((List<Number>)it)
            }
            else {
                throw new IllegalArgumentException("Invalid coordinates: $coords")
            }
        }
    }

}
