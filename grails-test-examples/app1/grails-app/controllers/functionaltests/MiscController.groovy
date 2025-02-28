package functionaltests

import org.springframework.beans.factory.annotation.*
import org.example.MyBean

class MiscController {

	@Value('${foo.bar}')
	String testProperty

    @Autowired
    MyBean myBean

    def beanPropertyOverrideTest() {
        render myBean.name()
    }


    def actionWhichReturnsNull() {
        null
    }

    def actionWhichReturnsMap() {
        [:]
    }

    def interceptedByInterceptor() {
    	// no op
    }

    def placeHolderConfig() {
    	def config = grailsApplication.config

    	render "[${config.foo.bar} ${config.getProperty('foo.bar')} ${testProperty}]"
    }
}
