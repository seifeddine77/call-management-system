// ============================================================
// Allow anonymous access to /prometheus/ metrics endpoint
// Required for Prometheus scraping without credentials
// ============================================================
import jenkins.model.Jenkins
import hudson.security.AuthorizationStrategy
import org.jenkinsci.plugins.prometheus.config.PrometheusConfiguration

def instance = Jenkins.getInstance()

// Configure Prometheus plugin for unauthenticated access
try {
    def config = PrometheusConfiguration.get()
    if (config != null) {
        config.setUseAuthenticatedEndpoint(false)
        config.setCountAbortedBuilds(true)
        config.setCountFailedBuilds(true)
        config.setCountNotBuiltBuilds(true)
        config.setCountSuccessfulBuilds(true)
        config.setCountUnstableBuilds(true)
        config.save()
        println('[init] Prometheus plugin: anonymous metrics endpoint enabled')
    }
} catch (Exception e) {
    println("[init] Prometheus plugin config error: ${e.message}")
}

// Mark install state as complete so Jenkins doesn't block /prometheus/ with setup wizard
try {
    if (!instance.installState.isSetupComplete()) {
        instance.installState = jenkins.install.InstallState.INITIAL_SETUP_COMPLETED
        instance.save()
        println('[init] Jenkins setup wizard marked as completed')
    }
} catch (Exception e) {
    println("[init] Setup state update skipped: ${e.message}")
}
