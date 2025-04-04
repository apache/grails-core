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
        if(coords.size() < 2) throw new DataAccessResourceFailureException("Invalid polygon data returned: $coords")

        def points = coords.collect() { List<Double> pos -> new Point(pos.get(0), pos.get(1)) }
        return new LineString(points as Point[])
    }
}
