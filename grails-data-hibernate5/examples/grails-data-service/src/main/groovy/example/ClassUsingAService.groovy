package example

import groovy.transform.CompileStatic

@CompileStatic
class ClassUsingAService {

    TestService testService

    void doSomethingWithTheService() {
        testService.testDataService(1l)
    }

}
