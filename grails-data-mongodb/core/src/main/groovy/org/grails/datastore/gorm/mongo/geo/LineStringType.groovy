/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.datastore.gorm.mongo.geo

import grails.mongodb.geo.LineString
import grails.mongodb.geo.Point
import groovy.transform.CompileStatic
import org.springframework.dao.DataAccessResourceFailureException

/**
 * Adds support for the {@link LineStringType} type to GORM for MongoDB
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class LineStringType extends GeoJSONType<LineString> {

    LineStringType() {
        super(LineString)
    }

    @Override
    LineString createFromCoords(List<List<Double>> coords) {
        if (coords.size() < 2) throw new DataAccessResourceFailureException("Invalid polygon data returned: $coords")

        def points = coords.collect() { List<Double> pos -> new Point(pos.get(0), pos.get(1)) }
        return new LineString(points as Point[])
    }
}
