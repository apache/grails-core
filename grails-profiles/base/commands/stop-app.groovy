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

String configuredPidFilePath = commandLine.systemProperties[RunningApplicationProcess.PID_FILE_CLI_PROPERTY] ?:
        System.getProperty(RunningApplicationProcess.PID_FILE_CLI_PROPERTY)
File pidFile = RunningApplicationProcess.pidFile(buildDir, configuredPidFilePath)

console.updateStatus "Stopping application..."

// Record that this is a deliberate stop before terminating the process, so a foreground run-app
// blocked on the bootRun build reports a clean shutdown rather than a startup failure.
RunningApplicationProcess.requestStop(buildDir)

switch (RunningApplicationProcess.stop(pidFile, 30000)) {
    case RunningApplicationProcess.StopResult.STOPPED:
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
