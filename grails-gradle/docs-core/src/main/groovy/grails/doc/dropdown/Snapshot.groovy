package grails.doc.dropdown


class Snapshot implements Comparable<Snapshot> {

    private String text

    int getMilestoneVersion() {
        text.replace("M", "").toInteger()
    }

    int getReleaseCandidateVersion() {
        text.replace("RC", "").toInteger()
    }

    boolean isBuildSnapshot() {
        text.endsWith("BUILD-SNAPSHOT")
    }

    boolean isReleaseCandidate() {
        text.startsWith("RC")
    }

    boolean isMilestone() {
        text.startsWith("M")
    }

    Snapshot(String text) {
        this.text = text
    }

    @Override
    int compareTo(Snapshot o) {

        if (this.buildSnapshot && !o.buildSnapshot) {
            return 1
        } else if (!this.buildSnapshot && o.buildSnapshot) {
            return -1
        } else if (this.buildSnapshot && o.buildSnapshot) {
            return 0
        }

        if (this.releaseCandidate && !o.releaseCandidate) {
            return 1
        } else if (!this.releaseCandidate && o.releaseCandidate) {
            return -1
        } else if (this.releaseCandidate && o.releaseCandidate) {
            return this.releaseCandidateVersion <=> o.releaseCandidateVersion
        }

        if (this.milestone && !o.milestone) {
            return 1
        } else if (!this.milestone && o.milestone) {
            return -1
        } else if (this.milestone && o.milestone) {
            return this.milestoneVersion <=> o.milestoneVersion
        }

        return 0
    }
}

