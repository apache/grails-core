description("Stops the running Grails application") {
    usage "grails stop-app"
    synonyms 'stop'
}

System.setProperty("run-app.running", "false")

console.updateStatus "Stopping application..."

if (org.grails.cli.gradle.RunningApplicationRegistry.stopAll()) {
    if (org.grails.cli.gradle.RunningApplicationRegistry.awaitStop(30000)) {
        console.updateStatus "Application stopped."
    }
    else {
        console.updateStatus "Application shutdown requested; it may still be stopping."
    }
    return true
}
else {
    console.error "Application not running."
    return false
}
