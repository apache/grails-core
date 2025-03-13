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
 * Represents a box for use in Geo data models
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@EqualsAndHashCode
@CompileStatic
class Box extends Shape{

    final Point lowerLeft, upperRight

    /**
     * Construct a box from 2 points representing the lower left corner and the uppper right corner
     *
     * @param lowerLeft The lower left point
     * @param upperRight The upper right point
     */
    Box(Point lowerLeft, Point upperRight) {
        this.lowerLeft = lowerLeft
        this.upperRight = upperRight
    }

    /**
     * Converts the Polygon to a multi-dimensional list of coordinates.
     * Example: [ [100.0, 0.0], [101.0, 0.0]]
     *
     * @return The list
     */
    List<List<Double>> asList() { [ lowerLeft.asList(), upperRight.asList() ] }

    @Override
    String toString() {
        asList().toString()
    }

    /**
     * Constructs a Box from the given coordinates
     * @param coords The coordinates
     * @return A box
     */
    static Box valueOf(List<List<Double>> coords) {
        if(coords.size() != 2) throw new IllegalArgumentException("Coordinates should contain at least 2 entries for a Box")

        new Box( Point.getPointAtIndex(coords, 0), Point.getPointAtIndex(coords, 1) )
    }
}
