package hyphenated

/**
 * Created by jameskleeh on 2/27/17.
 */
class NameSpacedController {

    static namespace = "abcXyz"

    static defaultAction = "barFoo"

    def returnsNull() {

    }

    def returnsMap() {
        [name: "Sally"]
    }

    def barFoo() {
        render "<html><body>default action</body></html>"
    }
}
