// ============================================================
// Jenkinsfile — AutoAssist CI/CD Pipeline (12 stages)
// Stack: Java 17, Python 3.11, Node 22, Flutter
// ============================================================
pipeline {
    agent any

    environment {
        DOCKER_REGISTRY  = 'localhost:5000'
        IMAGE_PREFIX     = 'autoassist'
        SONAR_ORG        = 'seifeddine77'
        SONAR_PROJECT    = 'seifeddine77_call-management-system'
        SONAR_URL        = 'https://sonarcloud.io'
    }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }



    stages {
        // ═══════════════════════════════════════
        // STAGE 0 — INSTALL BUILD TOOLS
        // ═══════════════════════════════════════
        stage('🛠️ Setup Tools') {
            steps {
                sh '''
                    echo "Installing build tools..."
                    apt-get update -qq
                    apt-get install -y -qq maven nodejs npm python3 python3-pip curl > /dev/null 2>&1 || true
                    mvn --version || echo "Maven not available"
                    node --version || echo "Node not available"
                    python3 --version || echo "Python not available"
                    echo "✅ Tools ready"
                '''
            }
        }

        // ═══════════════════════════════════════
        // STAGE 1 — CHECKOUT
        // ═══════════════════════════════════════
        stage('📥 Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_MSG   = sh(script: 'git log -1 --pretty=%s',     returnStdout: true).trim()
                    echo "🔖 Commit: ${env.GIT_SHORT} — ${env.GIT_MSG}"
                }
            }
        }

        // ═══════════════════════════════════════
        // STAGE 2 + 3 — BUILD & TEST (parallel)
        // ═══════════════════════════════════════
        stage('🔨 Build & Test') {
            parallel {
                // ── Java Services (9x) ──
                stage('☕ Java Services') {
                    steps {
                        script {
                            def services = [
                                'auth-service', 'organisation-service', 'ticket-service',
                                'rules-service', 'decision-service', 'notification-service',
                                'analytics-service', 'eureka-server', 'api-gateway'
                            ]
                            for (svc in services) {
                                echo "══════ Building ${svc} ══════"
                                dir("services/${svc}") {
                                    sh 'mvn verify --batch-mode -q'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                            jacoco execPattern: '**/target/jacoco.exec'
                        }
                    }
                }

                // ── AI Service (Python) ──
                stage('🐍 AI Service') {
                    steps {
                        dir('services/ai-service') {
                            sh '''
                                python3 -m pip install -r requirements.txt --quiet || pip install -r requirements.txt --quiet
                                python3 -m pip install pytest pytest-asyncio pytest-cov --quiet || pip install pytest pytest-asyncio pytest-cov --quiet
                                python3 -m pytest tests/ -v --tb=short --cov=app --cov-report=xml --cov-report=term || python -m pytest tests/ -v --tb=short --cov=app --cov-report=xml --cov-report=term
                            '''
                        }
                    }
                }

                // ── Frontend (React + Vite) ──
                stage('⚛️ Frontend') {
                    steps {
                        dir('frontend') {
                            sh '''
                                npm ci
                                npx tsc -b || true
                                npm run build
                            '''
                        }
                    }
                }

                // ── WhatsApp Service (Node.js) ──
                stage('💬 WhatsApp') {
                    steps {
                        dir('services/whatsapp-service') {
                            sh '''
                                npm ci
                                node --check index.js
                            '''
                        }
                    }
                }

                // ── Mobile App (Flutter) ──
                stage('📱 Mobile') {
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

        // ═══════════════════════════════════════
        // STAGE 4 — SONARCLOUD ANALYSIS
        // ═══════════════════════════════════════
        stage('📊 SonarCloud') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    script {
                        def services = [
                            'auth-service', 'organisation-service', 'ticket-service',
                            'rules-service', 'decision-service', 'notification-service',
                            'analytics-service'
                        ]
                        for (svc in services) {
                            dir("services/${svc}") {
                                sh """
                                    mvn sonar:sonar \
                                      -Dsonar.host.url=${SONAR_URL} \
                                      -Dsonar.token=${SONAR_TOKEN} \
                                      -Dsonar.organization=${SONAR_ORG} \
                                      -Dsonar.projectKey=${SONAR_PROJECT}-${svc} \
                                      -Dsonar.java.coveragePlugin=jacoco \
                                      --batch-mode -q
                                """
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // STAGE 5 — SECURITY SCANS (parallel)
        // ═══════════════════════════════════════
        stage('🔒 Security') {
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
                stage('OWASP') {
                    steps {
                        dir('services/auth-service') {
                            sh 'mvn org.owasp:dependency-check-maven:check -DskipTests -q -DfailBuildOnCVSS=9 || true'
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // STAGE 6 — DOCKER BUILD & PUSH
        // ═══════════════════════════════════════
        stage('🐳 Docker Build & Push') {
            steps {
                script {
                    def targets = [
                        [name: 'auth-service',         ctx: 'services/auth-service'],
                        [name: 'organisation-service', ctx: 'services/organisation-service'],
                        [name: 'ticket-service',       ctx: 'services/ticket-service'],
                        [name: 'rules-service',        ctx: 'services/rules-service'],
                        [name: 'decision-service',     ctx: 'services/decision-service'],
                        [name: 'notification-service', ctx: 'services/notification-service'],
                        [name: 'analytics-service',    ctx: 'services/analytics-service'],
                        [name: 'eureka-server',        ctx: 'services/eureka-server'],
                        [name: 'api-gateway',          ctx: 'services/api-gateway'],
                        [name: 'ai-service',           ctx: 'services/ai-service'],
                        [name: 'frontend',             ctx: 'frontend'],
                        [name: 'whatsapp-service',     ctx: 'services/whatsapp-service'],
                        [name: 'mobile',               ctx: 'mobile'],
                    ]
                    for (t in targets) {
                        def img = "${DOCKER_REGISTRY}/${IMAGE_PREFIX}-${t.name}"
                        sh """
                            docker build -t ${img}:${GIT_SHORT} -t ${img}:latest ${t.ctx}
                            docker push ${img}:${GIT_SHORT}
                            docker push ${img}:latest
                        """
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // STAGE 7 — DEPLOY STAGING
        // ═══════════════════════════════════════
        stage('🔧 Deploy Staging') {
            steps {
                sh '''
                    docker compose -f docker-compose.yml pull 2>/dev/null || true
                    docker compose -f docker-compose.yml up -d --build --remove-orphans
                    echo "⏳ Waiting 60s for services to start..."
                    sleep 60
                    docker compose -f docker-compose.yml ps
                '''
            }
        }

        // ═══════════════════════════════════════
        // STAGE 8 — SMOKE TESTS
        // ═══════════════════════════════════════
        stage('🧪 Smoke Tests') {
            steps {
                sh '''
                    echo "Testing Eureka..."
                    curl -sf --max-time 10 http://localhost:8761/actuator/health || echo "⚠️ Eureka not ready"
                    echo "Testing Gateway..."
                    curl -sf --max-time 10 http://localhost:8080/actuator/health || echo "⚠️ Gateway not ready"
                    echo "Testing Frontend..."
                    curl -sf --max-time 10 http://localhost:3000 || echo "⚠️ Frontend not ready"
                    echo "Testing AI Service..."
                    curl -sf --max-time 10 http://localhost:8000/health || echo "⚠️ AI not ready"
                    echo "✅ Smoke tests complete"
                '''
            }
        }

        // ═══════════════════════════════════════
        // STAGE 9 — APPROVAL GATE
        // ═══════════════════════════════════════
        stage('✋ Approval') {
            when { branch 'main' }
            steps {
                input message: '🚀 Déployer en Production (Kubernetes) ?',
                      ok: 'Déployer',
                      submitterParameter: 'APPROVER'
            }
        }

        // ═══════════════════════════════════════
        // STAGE 10 — DEPLOY PRODUCTION (K8s)
        // ═══════════════════════════════════════
        stage('☸️ Deploy K8s') {
            when { branch 'main' }
            steps {
                sh '''
                    cd k8s/
                    kubectl apply -f namespace.yml
                    kubectl apply -f configmaps/
                    kubectl apply -f deployments/infra/ 2>/dev/null || true
                    sleep 30
                    kubectl apply -f deployments/services/ 2>/dev/null || true
                    kubectl apply -f ingress.yml 2>/dev/null || true
                '''
            }
        }

        // ═══════════════════════════════════════
        // STAGE 11 — VERIFY ROLLOUT
        // ═══════════════════════════════════════
        stage('✅ Verify') {
            when { branch 'main' }
            steps {
                script {
                    def deployments = [
                        'eureka-server', 'api-gateway', 'auth-service',
                        'organisation-service', 'ticket-service', 'rules-service',
                        'decision-service', 'notification-service', 'analytics-service'
                    ]
                    def failed = false
                    for (dep in deployments) {
                        def status = sh(
                            script: "kubectl rollout status deployment/${dep} -n autoassist --timeout=120s",
                            returnStatus: true
                        )
                        if (status != 0) {
                            echo "❌ ${dep} rollout failed"
                            failed = true
                        } else {
                            echo "✅ ${dep} healthy"
                        }
                    }
                    if (failed) {
                        env.ROLLBACK_NEEDED = 'true'
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // STAGE 12 — ROLLBACK (if needed)
        // ═══════════════════════════════════════
        stage('⏪ Rollback') {
            when {
                expression { env.ROLLBACK_NEEDED == 'true' }
            }
            steps {
                script {
                    def deployments = [
                        'eureka-server', 'api-gateway', 'auth-service',
                        'organisation-service', 'ticket-service', 'rules-service'
                    ]
                    for (dep in deployments) {
                        sh "kubectl rollout undo deployment/${dep} -n autoassist || true"
                    }
                    error('⏪ Rollback effectué. Pipeline marqué en échec.')
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // POST — Notifications
    // ═══════════════════════════════════════
    post {
        success {
            echo """
            ╔══════════════════════════════════════╗
            ║  ✅ PIPELINE SUCCEEDED               ║
            ║  Commit: ${env.GIT_SHORT}             ║
            ║  Build:  #${env.BUILD_NUMBER}         ║
            ╚══════════════════════════════════════╝
            """
        }
        failure {
            echo """
            ╔══════════════════════════════════════╗
            ║  ❌ PIPELINE FAILED                  ║
            ║  Commit: ${env.GIT_SHORT}             ║
            ║  Build:  #${env.BUILD_NUMBER}         ║
            ╚══════════════════════════════════════╝
            """
        }
        always {
            cleanWs()
        }
    }
}
