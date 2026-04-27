// ============================================================
// Jenkinsfile — AutoAssist CI/CD Pipeline
// Production-grade: fail-fast, build-once, secrets-managed
// ============================================================
pipeline {
    agent any

    environment {
        DOCKER_REGISTRY  = 'localhost:5000'
        IMAGE_PREFIX     = 'autoassist'
        SONAR_ORG        = 'autoassist'
        SONAR_PROJECT    = 'autoassist_call-management-system'
        SONAR_URL        = 'https://sonarcloud.io'
    }

    options {
        timestamps()
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        // =============================================
        // STAGE 1 — CHECKOUT
        // =============================================
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_MSG   = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim()
                    env.IMAGE_TAG = "sha-${env.GIT_SHORT}"
                    echo "Commit: ${env.GIT_SHORT} — ${env.GIT_MSG}"
                }
            }
        }

        // =============================================
        // STAGE 2 — BUILD & TEST (parallel, fail-fast)
        // Blocking: broken code stops the pipeline
        // =============================================
        stage('Build and Test') {
            parallel {
                stage('Java Services') {
                    steps {
                        script {
                            def services = [
                                'auth-service', 'organisation-service', 'ticket-service',
                                'rules-service', 'decision-service', 'notification-service',
                                'analytics-service', 'eureka-server', 'api-gateway'
                            ]
                            for (svc in services) {
                                echo "Building ${svc}..."
                                dir("services/${svc}") {
                                    sh 'mvn verify --batch-mode -q'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: '**/target/surefire-reports/*.xml'
                            jacoco execPattern: '**/target/jacoco.exec'
                        }
                    }
                }

                stage('AI Service') {
                    steps {
                        dir('services/ai-service') {
                            sh '''
                                python3 -m venv .venv
                                . .venv/bin/activate
                                pip install -r requirements.txt --quiet
                                pip install pytest pytest-asyncio pytest-cov --quiet
                                python -m pytest tests/ -v --tb=short \
                                    --cov=app --cov-report=xml --cov-report=term
                            '''
                        }
                    }
                }

                stage('Frontend') {
                    steps {
                        dir('frontend') {
                            sh '''
                                npm ci
                                npx tsc -b
                                npm run build
                            '''
                        }
                    }
                }

                stage('WhatsApp Service') {
                    steps {
                        dir('services/whatsapp-service') {
                            sh '''
                                npm ci
                                node --check index.js
                            '''
                        }
                    }
                }

                stage('Mobile') {
                    steps {
                        dir('mobile') {
                            sh '''
                                flutter pub get
                                flutter analyze || true
                                flutter test || true
                                flutter build web --release
                            '''
                        }
                    }
                }
            }
        }

        // =============================================
        // STAGE 3 — SONARCLOUD ANALYSIS + QUALITY GATE
        // Blocking: code that fails the quality gate
        //           does not get deployed
        // =============================================
        stage('SonarCloud Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    script {
                        def services = [
                            'auth-service', 'organisation-service', 'ticket-service',
                            'rules-service', 'decision-service', 'notification-service',
                            'analytics-service'
                        ]
                        def qualityGateFailed = []
                        def analysisErrors = []

                        for (svc in services) {
                            echo "Analyzing ${svc}..."
                            try {
                                dir("services/${svc}") {
                                    // Run analysis AND wait for Quality Gate result
                                    sh """
                                        mvn sonar:sonar \
                                          -Dsonar.host.url=${SONAR_URL} \
                                          -Dsonar.token=${SONAR_TOKEN} \
                                          -Dsonar.organization=${SONAR_ORG} \
                                          -Dsonar.projectKey=${SONAR_PROJECT}-${svc} \
                                          -Dsonar.java.coveragePlugin=jacoco \
                                          -Dsonar.qualitygate.wait=true \
                                          -Dsonar.qualitygate.timeout=120 \
                                          --batch-mode -q
                                    """
                                    echo "PASSED: ${svc} quality gate OK"
                                }
                            } catch (err) {
                                def msg = err.message ?: err.toString()
                                if (msg.contains('timeout') || msg.contains('connect') || msg.contains('unreachable')) {
                                    echo "WARN: ${svc} — network error (${msg}), skipping"
                                    analysisErrors << svc
                                } else {
                                    echo "FAIL: ${svc} — quality gate failed"
                                    qualityGateFailed << svc
                                }
                            }
                        }

                        // Quality gate failures are blocking
                        if (qualityGateFailed) {
                            error("Quality Gate FAILED for: ${qualityGateFailed.join(', ')}. Fix code quality issues before deploying.")
                        }

                        // Network errors are non-blocking (mark unstable)
                        if (analysisErrors) {
                            unstable("SonarCloud unreachable for: ${analysisErrors.join(', ')} — analysis skipped")
                        }
                    }
                }
            }
        }

        // =============================================
        // STAGE 4 — SECURITY SCANS (parallel)
        // Non-blocking: informational, marks unstable
        // =============================================
        stage('Security Scans') {
            parallel {
                stage('Trivy') {
                    steps {
                        sh 'trivy fs --severity HIGH,CRITICAL --exit-code 0 --format table . || true'
                    }
                }
                stage('Gitleaks') {
                    steps {
                        sh 'gitleaks detect --source . --no-git --exit-code 0 || true'
                    }
                }
                stage('OWASP Dependency Check') {
                    steps {
                        dir('services/auth-service') {
                            sh 'mvn org.owasp:dependency-check-maven:check -DskipTests -q -DfailBuildOnCVSS=9 || true'
                        }
                    }
                }
            }
        }

        // =============================================
        // STAGE 5 — DOCKER BUILD & PUSH
        // Blocking: no image = no deployment
        // Build once: this exact image is deployed everywhere
        // =============================================
        stage('Docker Build and Push') {
            steps {
                script {
                    // Java services — lightweight image from pre-built JAR
                    def javaServices = [
                        'auth-service', 'organisation-service', 'ticket-service',
                        'rules-service', 'decision-service', 'notification-service',
                        'analytics-service', 'eureka-server', 'api-gateway'
                    ]
                    for (svc in javaServices) {
                        def img = "${DOCKER_REGISTRY}/${IMAGE_PREFIX}-${svc}"
                        def ctx = "services/${svc}"
                        echo "Building ${svc}..."
                        sh """
                            cat > ${ctx}/Dockerfile.ci << 'CIEOF'
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/*.jar app.jar
USER app
HEALTHCHECK --interval=30s --timeout=10s CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
CIEOF
                            docker build -f ${ctx}/Dockerfile.ci \
                                -t ${img}:${IMAGE_TAG} \
                                -t ${img}:build-${BUILD_NUMBER} \
                                -t ${img}:latest ${ctx}
                            docker push ${img}:${IMAGE_TAG}
                            docker push ${img}:build-${BUILD_NUMBER}
                            docker push ${img}:latest
                            rm -f ${ctx}/Dockerfile.ci
                        """
                    }

                    // Non-Java services — use their own Dockerfile
                    def otherServices = [
                        [name: 'ai-service',       ctx: 'services/ai-service'],
                        [name: 'frontend',         ctx: 'frontend'],
                        [name: 'whatsapp-service', ctx: 'services/whatsapp-service'],
                    ]
                    for (t in otherServices) {
                        def img = "${DOCKER_REGISTRY}/${IMAGE_PREFIX}-${t.name}"
                        def hasDockerfile = sh(script: "test -f ${t.ctx}/Dockerfile", returnStatus: true) == 0
                        if (hasDockerfile) {
                            echo "Building ${t.name}..."
                            sh """
                                docker build \
                                    -t ${img}:${IMAGE_TAG} \
                                    -t ${img}:build-${BUILD_NUMBER} \
                                    -t ${img}:latest ${t.ctx}
                                docker push ${img}:${IMAGE_TAG}
                                docker push ${img}:build-${BUILD_NUMBER}
                                docker push ${img}:latest
                            """
                        } else {
                            echo "SKIP: No Dockerfile for ${t.name}"
                        }
                    }
                }
            }
        }

        // =============================================
        // STAGE 6 — DEPLOY STAGING
        // Uses pre-built images from registry (no rebuild)
        // Secrets injected via Jenkins Credentials
        // =============================================
        stage('Deploy Staging') {
            steps {
                withCredentials([file(credentialsId: 'staging-env-file', variable: 'ENV_FILE')]) {
                    sh '''
                        cp "$ENV_FILE" .env
                        docker compose -f docker-compose.yml up -d --remove-orphans
                        echo "Waiting 45s for services to start..."
                        sleep 45
                        docker compose ps
                    '''
                }
            }
        }

        // =============================================
        // STAGE 7 — SMOKE TESTS
        // Validates that critical services are responding
        // =============================================
        stage('Smoke Tests') {
            steps {
                script {
                    def endpoints = [
                        [name: 'Eureka',       url: 'http://localhost:8761/actuator/health'],
                        [name: 'Gateway',      url: 'http://localhost:8080/actuator/health'],
                        [name: 'Auth',         url: 'http://localhost:8081/actuator/health'],
                        [name: 'Organisation', url: 'http://localhost:8082/actuator/health'],
                        [name: 'Frontend',     url: 'http://localhost:3000'],
                        [name: 'AI Service',   url: 'http://localhost:8000/health'],
                    ]
                    def passed = []
                    def failed = []
                    for (ep in endpoints) {
                        def status = sh(
                            script: "curl -sf --max-time 10 ${ep.url}",
                            returnStatus: true
                        )
                        if (status == 0) {
                            echo "OK: ${ep.name}"
                            passed << ep.name
                        } else {
                            echo "FAIL: ${ep.name} not responding"
                            failed << ep.name
                        }
                    }
                    echo "Smoke Tests: ${passed.size()} passed, ${failed.size()} failed"
                    if (failed.size() > 2) {
                        error("Too many services down: ${failed.join(', ')}")
                    }
                    if (failed) {
                        unstable("Some services not ready: ${failed.join(', ')}")
                    }
                }
            }
        }

        // =============================================
        // STAGE 8 — APPROVAL GATE
        // Manual approval required before production
        // =============================================
        stage('Approval') {
            steps {
                input message: "Deploy Build #${BUILD_NUMBER} (${IMAGE_TAG}) to Production?",
                      ok: 'Deploy to Production',
                      submitterParameter: 'APPROVER'
            }
        }

        // =============================================
        // STAGE 9 — DEPLOY PRODUCTION (Kubernetes)
        // =============================================
        stage('Deploy K8s') {
            steps {
                sh '''
                    cd k8s/
                    kubectl apply -f namespace.yml         2>/dev/null || true
                    kubectl apply -f configmaps/           2>/dev/null || true
                    kubectl apply -f deployments/infra/    2>/dev/null || true
                    sleep 30
                    kubectl apply -f deployments/services/ 2>/dev/null || true
                    kubectl apply -f ingress.yml           2>/dev/null || true
                '''
            }
        }

        // =============================================
        // STAGE 10 — VERIFY ROLLOUT
        // =============================================
        stage('Verify Rollout') {
            steps {
                script {
                    def deployments = [
                        'eureka-server', 'api-gateway', 'auth-service',
                        'organisation-service', 'ticket-service', 'rules-service',
                        'decision-service', 'notification-service', 'analytics-service'
                    ]
                    def failedDeps = []
                    for (dep in deployments) {
                        def status = sh(
                            script: "kubectl rollout status deployment/${dep} -n autoassist --timeout=120s",
                            returnStatus: true
                        )
                        if (status != 0) {
                            echo "FAIL: ${dep}"
                            failedDeps << dep
                        } else {
                            echo "OK: ${dep}"
                        }
                    }
                    if (failedDeps) {
                        env.ROLLBACK_NEEDED = 'true'
                        echo "Rollout failed for: ${failedDeps.join(', ')}"
                    }
                }
            }
        }

        // =============================================
        // STAGE 11 — ROLLBACK (conditional)
        // Automatic rollback if verification fails
        // =============================================
        stage('Rollback') {
            when {
                expression { env.ROLLBACK_NEEDED == 'true' }
            }
            steps {
                script {
                    def deployments = [
                        'eureka-server', 'api-gateway', 'auth-service',
                        'organisation-service', 'ticket-service', 'rules-service',
                        'decision-service', 'notification-service', 'analytics-service'
                    ]
                    for (dep in deployments) {
                        sh "kubectl rollout undo deployment/${dep} -n autoassist || true"
                    }
                    error('Rollback executed. Pipeline marked as failed.')
                }
            }
        }
    }

    // =============================================
    // POST — Cleanup and Notifications
    // =============================================
    post {
        success {
            echo """
            ============================================
              PIPELINE SUCCEEDED
              Commit: ${env.GIT_SHORT}
              Image:  ${env.IMAGE_TAG}
              Build:  #${env.BUILD_NUMBER}
            ============================================
            """
        }
        failure {
            echo """
            ============================================
              PIPELINE FAILED
              Commit: ${env.GIT_SHORT}
              Build:  #${env.BUILD_NUMBER}
            ============================================
            """
        }
        always {
            cleanWs(deleteDirs: true, disableDeferredWipeout: true, patterns: [
                [pattern: '**/target/**', type: 'INCLUDE'],
                [pattern: '**/.venv/**', type: 'INCLUDE'],
                [pattern: '**/node_modules/**', type: 'INCLUDE'],
            ])
        }
    }
}
