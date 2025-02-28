package functionaltests
class InspectConfigController {

    def showPropertyValues() {
        def env = applicationContext.environment
        def cfg = grailsApplication.config.grails11951
        def prop1 = cfg.prop1
        def prop2 = cfg.prop2
        def prop3 = cfg.prop3
        def prop1Flat = grailsApplication.config.getProperty('grails11951.prop1')
        def prop2Flat = grailsApplication.config.getProperty('grails11951.prop2')
        def prop3Flat = grailsApplication.config.getProperty('grails11951.prop3')
        [env:env,
          prop1: prop1,
          prop2: prop2,
          prop3: prop3,
          prop1Flat: prop1Flat,
          prop2Flat: prop2Flat,
          prop3Flat: prop3Flat]
    }
}
