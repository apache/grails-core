package redis

import grails.plugins.Plugin
import grails.plugins.redis.RedisService
import grails.plugins.redis.util.RedisConfigurationUtil

class RedisGrailsPlugin extends Plugin {

    def grailsVersion = "7.0.0-SNAPSHOT > *"
    def pluginExcludes = [
            "codenarc.properties",
            "grails-app/conf/**",
            "grails-app/views/**",
            "grails-app/domain/**",
            "grails-app/services/test/**"
    ]

    def title = "Redis Plugin" // Headline display name of the plugin
    def author = "Ted Naleid"
    def authorEmail = "contact@naleid.com"

    def description = '''The Redis plugin provides integration with a Redis datastore. Redis is a lightning fast 'data structure server'.  The plugin enables a number of memoization techniques to cache results from complex operations in Redis.'''
    def issueManagement = [system: 'github', url: 'https://github.com/grails-plugins/grails-redis/issues']
    def scm = [url: "https://github.com/grails-plugins/grails-redis"]
    def documentation = "http://grails.org/plugin/grails-redis"
    def license = "APACHE"

    def developers = [
            [name: "Burt Beckwith"],
            [name: "Christian Oestreich"],
            [name: "Brian Coles"],
            [name: "Michael Cameron"],
            [name: "John Engelman"],
            [name: "David Seiler"],
            [name: "Jordon Saardchit"],
            [name: "Florian Langenhahn"],
            [name: "German Sancho"],
            [name: "John Mulhern"],
            [name: "Shaun Jurgemeyer"]]

    Closure doWithSpring() {
        { ->
            def redisConfigMap = grailsApplication.config.get('grails.redis') ?: [:]

            RedisConfigurationUtil.configureService(delegate, redisConfigMap, "", RedisService)
            redisConfigMap?.connections?.each { connection ->
                RedisConfigurationUtil.configureService(delegate, connection.value, connection?.key?.capitalize(), RedisService)
            }
        }
    }
}
