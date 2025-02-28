package functionaltests


class ErrorThrowingInterceptor {

    ErrorThrowingInterceptor() {
        match(controller:"errors", action:"throwErrorInInterceptor")
    }

    boolean before() { 
        throw new CustomException("Interceptor threw error")
        true 
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }
}
