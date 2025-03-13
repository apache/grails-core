package functional.tests

import grails.mongodb.geo.Point

/**
 * Created by graemerocher on 19/05/16.
 */
class PlaceController {

    static responseFormats = ['json']

    def show() {
        respond new Place(name: "London", location: Point.valueOf(10,10))
    }

    def test() {
        respond new Place(name: "London", location: Point.valueOf(10,10))
    }
}

class Place {
    String name
    Point location
}