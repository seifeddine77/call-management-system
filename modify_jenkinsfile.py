import re

with open('Jenkinsfile', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = re.compile(r"        stage\('SonarCloud Analysis'\) \{.*?        // =============================================\n        // STAGE 4", re.DOTALL)

replacement = """        stage('SonarCloud Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh \"\"\"
                        sonar-scanner \\
                          -Dsonar.host.url=${SONAR_URL} \\
                          -Dsonar.token=${SONAR_TOKEN} \\
                          -Dsonar.organization=${SONAR_ORG} \\
                          -Dsonar.projectKey=${SONAR_PROJECT} \\
                          -Dsonar.qualitygate.wait=true \\
                          -Dsonar.qualitygate.timeout=120
                    \"\"\"
                }
            }
        }

        // =============================================
        // STAGE 4"""

new_content = pattern.sub(replacement, content)
with open('Jenkinsfile', 'w', encoding='utf-8') as f:
    f.write(new_content)
