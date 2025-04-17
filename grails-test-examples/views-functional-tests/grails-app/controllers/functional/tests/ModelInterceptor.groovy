package functional.tests

class ModelInterceptor {

    Object latestModel

    ModelInterceptor() {
        match(controller: 'modelAndView|respond|return')
    }

    boolean before() {
        true
    }

    boolean after() {
        latestModel = model
        true
    }
}
