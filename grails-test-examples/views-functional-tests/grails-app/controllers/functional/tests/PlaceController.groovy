package functional.tests

/**
 * Created by graemerocher on 19/05/16.
 */
class PlaceController {

    static responseFormats = ['json']

    def test() {
        respond new Place(name: "London", location: "UK")
    }
}

class Place {
    String name
    String location
}