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
 * Represents a Sphere with the radius calculated in radians
 *
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@EqualsAndHashCode
@CompileStatic
class Sphere extends Shape{

    /**
     * The center of there sphere
     */
    final Point center

    /**
     * The distance used to calculate the radius in radians
     */
    final Distance distance

    /**
     * Construct a circle for the given center {@link Point} and distance
     * @param center The center {@link Point}
     * @param distance The distance
     */
    Sphere(Point center, Distance distance) {
        this.center = center
        this.distance = distance
    }

    /**
     * The sphere coordinate list with the radius in radians
     *
     * @return A coordinate list
     */
    @Override
    List<? extends Object> asList() {
        [ center.asList(), distance.inRadians() ]
    }

    /**
     * Constructs a Sphere for the given coordinate list and optional metric
     *
     * @param coords The coordinate list
     * @param metric The metric to use to calculate radians (defaults to {@link Metric#NEUTRAL}
     *
     * @return A Sphere
     */
    static Sphere valueOf( List coords, Metric metric = Metric.NEUTRAL) {
        if(coords.size() < 2) throw new IllegalArgumentException("Coordinates should contain at least 2 entries for a Sphere: The center point and the distance")

        Point center = Point.getPointAtIndex(coords, 0)
        def ro = coords.get(1)
        Double distance = null
        if(ro instanceof Number)
            distance = ((Number) ro).doubleValue()

        if(center && distance != null) {
            return new Sphere(center, Distance.valueOf(distance, metric) )
        }
        else {
            throw new IllegalArgumentException("Invalid Sphere coordinates: $coords")
        }
    }
}
