package functionaltests

import grails.artefact.Interceptor

class ErrorsControllerInterceptor implements Interceptor {

    static final String PROPERTY = "exceptionMessage"

    ErrorsControllerInterceptor() {
        match(controller:"errors", action:"throwException")
    }

    @Override
    void afterView() {
        System.setProperty(PROPERTY, throwable.message)
    }
}
