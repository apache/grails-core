package functionaltests


class RedirectInterceptor {

	RedirectInterceptor() {
		match(controller:'misc', action:'interceptedByInterceptor')
	}

    boolean before() { 
    	redirect(controller:'includes', action:'textRendering')
	}

    boolean after() { true }

    void afterView() {
        // no-op
    }

}
