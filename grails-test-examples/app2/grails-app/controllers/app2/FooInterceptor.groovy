package app2

class FooInterceptor {

    boolean before() {
        if(params.user != 'admin') {
            response.sendError 404
            return false
        }
        return true
    }
}