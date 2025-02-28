package functionaltests

import grails.rest.Resource

@Resource(uri = '/stuffs')
class Stuff {
	String name
}
