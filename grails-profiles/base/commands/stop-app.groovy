import org.grails.cli.gradle.RunningApplicationProcess

description("Stops the running Grails application") {
    usage "grails stop-app"
    synonyms 'stop'
    flag name:'port', description:'No longer supported. Stops the run-app process for the current project.'
    flag name:'host', description:'No longer supported. Stops the run-app process for the current project.'
}

if (commandLine.hasOption('port') || commandLine.hasOption('host')) {
    console.error 'The --port and --host options are no longer supported. ' +
            'stop-app stops the application started by run-app for the current project.'
    return false
}

System.setProperty("run-app.running", "false")

File pidFile = RunningApplicationProcess.pidFile(buildDir)

console.updateStatus "Stopping application..."

// Record a deliberate stop before terminating the process. This marker has nothing to do with how
// the application is shut down - that is always a graceful ProcessHandle.destroy() (SIGTERM on
// Unix), so JVM shutdown hooks and Spring's orderly shutdown still run. It exists only to classify
// the message a foreground, blocking `grails run-app` prints: because stop-app terminates the forked
// application JVM (not the Gradle bootRun process), that build returns a non-zero child exit, which
// would otherwise be reported as a startup failure. The marker is cleared at the start of the next
// run-app, so it can never mask a genuine failure.
RunningApplicationProcess.requestStop(buildDir)

switch (RunningApplicationProcess.stop(pidFile, 30000)) {
    case RunningApplicationProcess.StopResult.STOPPED:
        // Leave the marker in place: a foreground run-app blocked on the bootRun build may not have
        // observed the terminated process yet, and clearing it here would race that check. It is
        // cleared by the next run-app instead.
        console.updateStatus "Application stopped."
        return true
    case RunningApplicationProcess.StopResult.STILL_RUNNING:
        console.error "Application did not stop within the timeout; it may still be shutting down."
        return false
    default:
        RunningApplicationProcess.clearStopRequest(buildDir)
        console.error "Application not running."
        return false
}
