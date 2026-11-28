package example.grails

class FooBarInterceptor {

      boolean before() { true }

      boolean after() { true }

      void afterView() {
        // no-op
      }

}
