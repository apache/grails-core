package gorm

class UserService {

    List bycity(Long id) {
        User.where { city.id == id }.list()
    }
}
