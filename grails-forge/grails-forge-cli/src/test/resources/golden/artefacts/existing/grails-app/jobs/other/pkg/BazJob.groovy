package other.pkg

class BazJob {

    static triggers = {
        simple repeatInterval: 5000L // execute the job every 5 seconds
    }

    def execute() {
        // execute job
    }
}
