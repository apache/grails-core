package demo

class InterceptorDemoController {

    def one() {
        if(params.didUriMatchingInterceptorMatch) {
            render '<html><body>interceptor did match for the one action</body></html>'
        } else {
            render '<html><body>interceptor did not match for the one action</body></html>'
        }
    }

    def two() {
        if(params.didUriMatchingInterceptorMatch) {
            render '<html><body>interceptor did match for the two action</body></html>'
        } else {
            render '<html><body>interceptor did not match for the two action</body></html>'
        }
    }
}
