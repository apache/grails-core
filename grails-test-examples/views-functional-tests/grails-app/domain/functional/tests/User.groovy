package functional.tests

class User {
    String publicId
    String username

    static mapping = {
        table name: '`user`'
    }
}
