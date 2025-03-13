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
 * Defines a metric for calculating {@link Distance} in radians. See {@link Distance#inRadians()} }
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@EqualsAndHashCode
@CompileStatic
public class Metric {
    /** the radius of the earth in kilometers **/
    static Metric KILOMETERS = new Metric(6378.137d)
    /** the radius of the earth in miles **/
    static Metric MILES = new Metric(3963.191d)
    /**  a neutral radius **/
    static Metric NEUTRAL = new Metric(1d)

    final double multiplier

    /**
     * Constructs a metric for the given multiplier
     * @param multiplier The multiplier
     */
    Metric(double multiplier) {
        this.multiplier = multiplier
    }
}