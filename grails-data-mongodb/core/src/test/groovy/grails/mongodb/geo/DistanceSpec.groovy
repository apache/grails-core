/* Copyright (C) 2014 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.mongodb.geo

import spock.lang.Specification
import spock.lang.Unroll

class DistanceSpec extends Specification {

    void "the metric defaults to NEUTRAL"() {
        expect:
        new Distance(5.0d).metric == Metric.NEUTRAL
    }

    @Unroll
    void "inRadians divides the value by the metric's multiplier for #metric"() {
        expect:
        new Distance(value, metric).inRadians() == value / metric.multiplier

        where:
        value  | metric
        1.0d   | Metric.NEUTRAL
        10.0d  | Metric.KILOMETERS
        10.0d  | Metric.MILES
        0.0d   | Metric.KILOMETERS
    }

    void "valueOf creates a Distance with the given value and metric"() {
        when:
        Distance distance = Distance.valueOf(42.0d, Metric.MILES)

        then:
        distance.value == 42.0d
        distance.metric == Metric.MILES
    }

    void "valueOf defaults to NEUTRAL when no metric is given"() {
        when:
        Distance distance = Distance.valueOf(42.0d)

        then:
        distance.metric == Metric.NEUTRAL
    }

    void "two distances with the same value and metric are equal"() {
        expect:
        new Distance(5.0d, Metric.KILOMETERS) == new Distance(5.0d, Metric.KILOMETERS)
        new Distance(5.0d, Metric.KILOMETERS) != new Distance(5.0d, Metric.MILES)
        new Distance(5.0d, Metric.KILOMETERS) != new Distance(6.0d, Metric.KILOMETERS)
    }
}
