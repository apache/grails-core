package grails.plugin.json.view

import grails.views.resolve.GenericGroovyTemplateResolver
import spock.lang.Specification

/**
 * Created by graemerocher on 24/08/15.
 */
class JsonTemplateCompilerSpec extends Specification {

    void "Test JsonTemplateCompiler compiles templates correctly"() {
        given:"A compiler instance"
        def view = new File(JsonTemplateCompilerSpec.getResource("/views/bar.gson").file)

        def config = new JsonViewConfiguration(packageName: "test")
        def compiler = new JsonViewCompiler(config, view.parentFile)

        def dir = File.createTempDir()
        dir.deleteOnExit()
        compiler.setTargetDirectory(dir)
        def resolver = new GenericGroovyTemplateResolver(packageName: "test")
        resolver.classLoader = new URLClassLoader([dir.toURL()] as URL[])
        def engine = new JsonViewTemplateEngine(config, Thread.currentThread().contextClassLoader)
        engine.templateResolver = resolver
        when:"templates are compiled"

        compiler.compile(view)

        then:"The template can be loaded"
        engine.resolveTemplate("/bar.gson") != null
    }

    void "Test JsonTemplateCompiler compiles templates correctly where the view has a package name"() {
        given:"A compiler instance"
        def view = new File(JsonTemplateCompilerSpec.getResource("/views/bar2.gson").file)

        def config = new JsonViewConfiguration(packageName: "test")
        def compiler = new JsonViewCompiler(config, view.parentFile)

        def dir = File.createTempDir()
        dir.deleteOnExit()
        compiler.setTargetDirectory(dir)
        def resolver = new GenericGroovyTemplateResolver(packageName: "test")
        resolver.classLoader = new URLClassLoader([dir.toURL()] as URL[])
        def engine = new JsonViewTemplateEngine(config, Thread.currentThread().contextClassLoader)
        engine.templateResolver = resolver
        when:"templates are compiled"

        compiler.compile(view)

        then:"The template can be loaded"
        engine.resolveTemplate("/bar2.gson") != null
    }
}


