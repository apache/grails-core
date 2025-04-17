package functional.tests

/**
 * Created by graemerocher on 10/12/15.
 */
class Team {
    String name
    Player captain
    List players
    static hasMany = [players: Player]
}