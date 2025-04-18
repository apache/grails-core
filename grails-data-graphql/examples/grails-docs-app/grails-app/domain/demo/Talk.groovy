// tag::wholeFile[]
package demo

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class Talk {

    String title
    int duration

    static belongsTo = [speaker: Speaker]
}
// end::wholeFile[]
