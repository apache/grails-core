package gorm

class City {

    String name
    static hasMany = [users:User]

    static constraints = {
    }
}
