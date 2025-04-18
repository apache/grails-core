package example

class TestService {

    LibraryService libraryService

    Boolean testDataService(Serializable id)  {
        libraryService.bookExists(id)
    }

    Person save(String firstName, String lastName) {
        libraryService.addMember(firstName, lastName)
    }
}
