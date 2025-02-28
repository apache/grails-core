package demo


class UriMatchingInterceptor {

    public UriMatchingInterceptor() {
        match uri: '/interceptorDemo/one'
    }

    boolean before() {
        params.didUriMatchingInterceptorMatch = true
        true
    }
}
