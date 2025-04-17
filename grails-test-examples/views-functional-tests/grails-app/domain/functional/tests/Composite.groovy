package functional.tests

/**
 * Created by jameskleeh on 2/6/17.
 */
class Composite implements Serializable {

    String name
    Player player
    Team team

    static mapping = {
        id composite: ['player', 'team']
    }

}
