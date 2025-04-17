package org.grails.scaffolding


class ClosureCapture {
    boolean stopOnException = true
    private boolean hasException = false
    private RootCall root = new RootCall(calls: [])
    private RootCall current = root
    List<Call> getCalls() { return root.calls }
    def invokeMethod(String name, args) {
        Call call = new Call(name: name, args: args, parent: current, calls: [], throwable: null)
        current.calls << call
        if(args && args[-1] instanceof Closure) {
            RootCall previousCall = current
            current = call
            Closure c = args[-1]
            def previousDelegate = c.delegate
            c.delegate = this
            try {
                c.call()
            } catch(Throwable t) {
                call.throwable = t
                hasException = true
                if(stopOnException) {
                    throw t
                }
            } finally {
                c.delegate = previousDelegate
            }
            current = previousCall
        }
    }

    private static class RootCall {
        @Delegate
        List<Call> calls
    }

    private static class Call extends RootCall {
        String name
        def args
        RootCall parent
        Throwable throwable
    }
}