package functionaltests


class BeforeInterceptor {

    BeforeInterceptor() {
        match controller: 'demo', action: 'another'
    }

    boolean before() { 
		render view: '/fromFilter', model: [name: 'JSB']
    	return false
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }
}
