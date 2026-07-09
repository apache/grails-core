package gorm

class AccessToken {

    String tokenValue
    String username

    static constraints = {
        tokenValue nullable: false
        username nullable: false
    }

    static mapping = {
        version false
    }

}