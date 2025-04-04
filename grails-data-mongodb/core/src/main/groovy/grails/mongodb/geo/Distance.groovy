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

import groovy.transform.EqualsAndHashCode

/**
 * Represents the distance from one {@link Point} to another
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@EqualsAndHashCode
class Distance {

    /**
     * The value representing the distance
     */
    final double value
    /**
     * The metric used to calculate radians. See {@link Distance#inRadians()}
     */
    final Metric metric

    /**
     * Constructs a distance for the given value and optional metric
     *
     * @param value The value
     * @param metric The metric
     */
    Distance(double value, Metric metric = Metric.NEUTRAL) {
        this.value = value
        this.metric = metric
    }

    /**
     * Create a Distance instance for the given value
     *
     * @param value The value
     * @param metric The metric
     * @return The distance
     */
    static Distance valueOf(double value, Metric metric = Metric.NEUTRAL) {
        new Distance(value, metric)
    }

    /**
     * @return The value of the distance in Radians
     */
    double inRadians() {
        value / metric.multiplier
    }
}
