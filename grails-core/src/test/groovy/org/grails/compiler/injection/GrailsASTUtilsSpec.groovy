package org.grails.compiler.injection

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Issue
import spock.lang.Specification

class GrailsASTUtilsSpec extends Specification {

    @Issue('apache/grails-core#10079')
    void 'test domain class detection when the current source unit is associated with a controller'() {
        setup:
        File tmpDir = new File(System.getProperty('java.io.tmpdir'))

        File projectDir = new File(tmpDir, "projectDir")

        // create /projectDir/grails-app/domain/ under java.io.tmpdir
        File grailsAppDir = new File(projectDir, 'grails-app')
        File domainDir = new File(grailsAppDir, 'domain')

        String packagePath = Something.package.name.replace('.' as char, File.separatorChar)

        // create the source file that would contain the source for the
        // relevant domain class...
        File domainPackageDir = new File(domainDir, packagePath)
        domainPackageDir.mkdirs()
        File domainClassFile = new File(domainPackageDir, 'Something.groovy')
        domainClassFile.createNewFile()

        // the controller source file doesn't really need to exist but we need a
        // fully qualified path to where it would be...
        File controllersDir = new File(grailsAppDir, 'controllers')
        File controllerPackageDir = new File(controllersDir, packagePath)
        File controllerClassFile = new File(controllerPackageDir,
                                            'SomethingController.groovy')

        SourceUnit controllerSourceUnit = Mock()
        controllerSourceUnit.getName() >> controllerClassFile.absolutePath

        expect: 'Something should be recognized as a domain because grails-app/domain/org/grails/compiler/injection/Something.groovy exists'
        GrailsASTUtils.isDomainClass(new ClassNode(Something), controllerSourceUnit)

        and: 'SomethingElse should NOT be recognized as a domain because grails-app/domain/org/grails/compiler/injection/SomethingElse.groovy does NOT exist'
        !GrailsASTUtils.isDomainClass(new ClassNode(SomethingElse), controllerSourceUnit)
    }
}

class Something {}
class SomethingElse {}
