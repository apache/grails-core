package org.grails.scaffolding

import spock.lang.Specification

/**
 * Created by Jim on 6/6/2016.
 */
abstract class ClosureCaptureSpecification extends Specification {

    protected ClosureCapture getClosureCapture(Closure closure) {
        ClosureCapture closureCapture = new ClosureCapture()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = closureCapture
        closure.call()
        closureCapture
    }

}
