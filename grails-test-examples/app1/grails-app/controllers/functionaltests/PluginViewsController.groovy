package functionaltests

class PluginViewsController {

    def testTemplateFromPlugin() {}

    def testPluginViewOverrideInPlugin() {
        render view:'/override/me'
    }
}
