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
 * Represents a GeoJSON MultiLineString. See http://geojson.org/geojson-spec.html#multilinestring
 *
 * Note: Requires MongoDB 2.6 or above
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@EqualsAndHashCode
class MultiLineString extends Shape implements GeoJSON{
    final List<LineString> coordinates

    MultiLineString(LineString... coordinates) {
        this.coordinates = Arrays.asList(coordinates)
    }

    MultiLineString(List<LineString> coordinates) {
        Assert.notNull(coordinates, "Argument 'coordinates' is required")
        this.coordinates = coordinates
    }

    @Override
    List<List<List<Double>>> asList() {
        coordinates.collect() { LineString ls -> ls.asList() }
    }

    @Override
    String toString() {
        coordinates.toString()
    }

    static MultiLineString valueOf(List coords) {
        List<LineString> lineStrings = (List<LineString>) coords.collect() {
            if(it instanceof LineString) {
                return it
            }
            else if(it instanceof List) {
                return LineString.valueOf((List)it)
            }
            throw new IllegalArgumentException("Invalid coordinates: $coords")
        }

        return new MultiLineString(lineStrings as LineString[])
    }
}
