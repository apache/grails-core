package functionaltests

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

class Application extends GrailsAutoConfiguration {
	static {
		System.setProperty('foo.bar', 'test')
	}
	
    static void main(String[] args) {
        GrailsApp.run(Application)
    }
}