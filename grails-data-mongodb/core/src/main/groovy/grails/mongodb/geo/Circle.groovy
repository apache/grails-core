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
 * Represents a circle with the radius specified in the unit of the coordinate system
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@EqualsAndHashCode
@CompileStatic
class Circle extends Shape{
    /**
     * The center of the circle
     */
    final Point center
    /**
     * The radius of the circle
     */
    final double radius

    /**
     * Construct a circle for the given center {@link Point} and radius
     * @param center The center {@link Point}
     * @param radius The radius in meters
     */
    Circle(Point center, double radius) {
        Assert.notNull(center, "Argument center cannot be null")
        this.center = center
        this.radius = radius
    }

    /**
     * @return The circle as a coordinate list
     */
    List<Object> asList() { [ center.asList(), radius] }

    @Override
    String toString() { "[$center, $radius]" }

    /**
     * Construct a circle for the given coordinates
     *
     * @param coords The coordinates
     * @return The Circle instance
     */
    static Circle valueOf(List<Object> coords) {
        if(coords.size() < 2) throw new IllegalArgumentException("Coordinates should contain at least 2 entries for a Circle: The center point and the radius")

        Point center = Point.getPointAtIndex(coords, 0)
        def ro = coords.get(1)
        Number radius = null
        if(ro instanceof Number)
            radius = (Number) ro

        if(center && radius != null) {
            return new Circle(center, radius.doubleValue())
        }
        else {
            throw new IllegalArgumentException("Invalid Circle coordinates: $coords")
        }
    }
}
