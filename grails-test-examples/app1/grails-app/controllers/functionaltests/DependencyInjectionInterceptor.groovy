package functionaltests


class DependencyInjectionInterceptor {

    def magicNumberService

    DependencyInjectionInterceptor() {
        match(controller:"misc", action:"actionWhichReturnsMap")
    }

    boolean before() { true }

    boolean after() {
        model.magicNumber = magicNumberService.magicNumber
        true
    }

    void afterView() {
        // no-op
    }

}
