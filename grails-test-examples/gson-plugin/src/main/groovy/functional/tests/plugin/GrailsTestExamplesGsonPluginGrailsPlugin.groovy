package functional.tests.plugin


import grails.plugins.Plugin

class GrailsTestExamplesGsonPluginGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "7.0.0-SNAPSHOT > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Functional Tests Plugin" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']
    def documentation = "https://grails.org/plugin/examples-functional-tests-plugin"
    def license = "APACHE"
}
