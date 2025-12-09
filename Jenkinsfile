@Library('advanced-complex-lib@main') _  // Using 'main' branch, replace with your desired version

pipeline {
    agent any
    
    parameters {
        // Repository configuration
        string(
            name: 'GIT_REPO_URL',
            defaultValue: 'https://github.com/spring-projects/spring-petclinic.git',
            description: 'Git repository URL to build'
        )
        string(
            name: 'GIT_BRANCH',
            defaultValue: 'main',
            description: 'Git branch to build from'
        )
        string(
            name: 'GIT_CREDENTIALS_ID',
            defaultValue: '',
            description: 'Jenkins credentials ID for Git repository (leave empty for public repos)'
        )
        
        // Build parameters
        choice(
            name: 'BUILD_TOOL',
            choices: ['maven', 'gradle'],
            description: 'Build tool to use'
        )
        
        // Test parameters
        booleanParam(
            name: 'RUN_UNIT_TESTS',
            defaultValue: true,
            description: 'Run unit tests'
        )
        booleanParam(
            name: 'RUN_INTEGRATION_TESTS',
            defaultValue: false,
            description: 'Run integration tests'
        )
        
        // Code quality parameters
        booleanParam(
            name: 'RUN_CODE_ANALYSIS',
            defaultValue: true,
            description: 'Run static code analysis'
        )
        booleanParam(
            name: 'SKIP_BUILD_STATIC_CHECKS',
            defaultValue: true,
            description: 'Skip Checkstyle/PMD/SpotBugs during the Build stage; Code Quality stage will still run reports'
        )
        booleanParam(
            name: 'RUN_SECURITY_SCAN',
            defaultValue: true,
            description: 'Run security scans'
        )
        booleanParam(
            name: 'GENERATE_DOCS',
            defaultValue: false,
            description: 'Generate API documentation'
        )
        
        // Notifications
        booleanParam(
            name: 'DISABLE_NOTIFICATIONS',
            defaultValue: false,
            description: 'Disable post-build notifications (e.g., Slack)'
        )
        string(
            name: 'SLACK_CHANNEL',
            defaultValue: '#builds',
            description: 'Slack channel to send notifications (e.g., #builds). Leave blank to use Jenkins default.'
        )
        
        // Advanced options
        booleanParam(
            name: 'PARALLEL_BUILD',
            defaultValue: true,
            description: 'Enable parallel build where possible'
        )
        string(
            name: 'BUILD_ARGS',
            defaultValue: '',
            description: 'Additional build arguments'
        )
    }
    
    environment {
        // Application configuration
        APP_NAME = 'spring-petclinic'
        APP_VERSION = '3.1.0'
        

        // Docker configuration
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_IMAGE = "${DOCKER_REGISTRY}/${APP_NAME}"
        
        // Build configuration
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository -Dmaven.test.failure.ignore=true'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout()
    }
    
    // Jenkins-managed tools. Configure these names in Manage Jenkins > Global Tool Configuration.
    tools {
        // JDK 17 installation name (configure in Jenkins as 'jdk17' pointing to /usr/lib/jvm/java-1.17.0-openjdk-amd64 or similar)
        jdk 'jdk17'
        // Uncomment if you also configure a Maven installation in Jenkins (e.g., 'maven3')
        // maven 'maven3'
    }
    
    stages {
        stage('Initialize') {
            steps {
                script {
                    // Ensure JAVA_HOME is set correctly on this agent
                    try {
                        if (!env.JAVA_HOME || !fileExists("${env.JAVA_HOME}/bin/java")) {
                            def javaBin = sh(script: 'which java', returnStdout: true).trim()
                            if (javaBin) {
                                // JAVA_HOME is the parent directory of bin
                                def javaHome = sh(script: 'dirname "$(dirname "' + javaBin + '")"', returnStdout: true).trim()
                                env.JAVA_HOME = javaHome
                                env.PATH = "${javaHome}/bin:${env.PATH}"
                            }
                        }
                        sh 'java -version'
                    } catch (e) {
                        echo "Warning: Unable to validate JAVA_HOME automatically: ${e.message}"
                    }

                    // Print build information
                    echo """
                    ====== Build Information ======
                    Application: ${APP_NAME} v${APP_VERSION}
                    Build URL: ${env.BUILD_URL}
                    Build Number: ${env.BUILD_NUMBER}
                    Node: ${env.NODE_NAME}
                    Workspace: ${env.WORKSPACE}
                    Build Tool: ${params.BUILD_TOOL}
                    Branch: ${params.GIT_BRANCH}
                    ===============================
                    """
                    
                    // Set build display name
                    currentBuild.displayName = "${APP_VERSION}-${env.BUILD_NUMBER}"
                }
            }
        }
        
        stage('Checkout') {
            steps {
                script {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${params.GIT_BRANCH}"]],
                        extensions: [
                            [$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true],
                            [$class: 'CleanBeforeCheckout']
                        ],
                        userRemoteConfigs: [[
                            url: "${params.GIT_REPO_URL}",
                            credentialsId: params.GIT_CREDENTIALS_ID ?: null
                        ]]
                    ])
                    
                    // Set Git environment variables
                    env.GIT_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.GIT_SHORT_COMMIT = env.GIT_COMMIT.take(8)
                    env.GIT_BRANCH = params.GIT_BRANCH
                    
                    echo "Building commit: ${env.GIT_COMMIT} on branch: ${env.GIT_BRANCH}"
                    echo "Repository: ${params.GIT_REPO_URL}"
                }
            }
        }
        
        stage('Build') {
            steps {
                script {
                    def buildArgs = []
                    
                    // Add build tool specific arguments
                    if (params.BUILD_TOOL == 'maven') {
                        buildArgs << 'clean package'
                        
                        if (params.PARALLEL_BUILD) {
                            buildArgs << '-T 1C'  // Use one thread per CPU core
                        }
                        
                        // Skip tests if needed (they'll run in separate stages)
                        if (params.RUN_UNIT_TESTS == false) {
                            buildArgs << '-DskipTests'
                        }
                        
                        if (params.RUN_INTEGRATION_TESTS == false) {
                            buildArgs << '-DskipITs'
                        }
                        
                        // Add any additional build arguments
                        if (params.BUILD_ARGS) {
                            buildArgs << params.BUILD_ARGS
                        }
                        
                        // Optionally skip static checks that may fail the build
                        if (params.SKIP_BUILD_STATIC_CHECKS) {
                            buildArgs << '-Dcheckstyle.skip=true'
                            buildArgs << '-Dpmd.skip=true'
                            buildArgs << '-Dspotbugs.skip=true'
                            // Additional flags helpful for Spring projects
                            buildArgs << '-Dcheckstyle.failOnViolation=false'
                            buildArgs << '-Dpmd.failOnViolation=false'
                            buildArgs << '-Dspotbugs.failOnError=false'
                            buildArgs << '-Dnohttp.skip=true'
                            buildArgs << '-Dnohttp.check.skip=true'
                            // Common umbrella skip property used in many Spring builds
                            buildArgs << '-DskipChecks'
                            // Disable nohttp enforcement fully if present
                            buildArgs << '-Dnohttp=false'
                        }
                        
                        sh "mvn ${buildArgs.join(' ')}"
                        
                    } else if (params.BUILD_TOOL == 'gradle') {
                        buildArgs << 'clean build'
                        
                        if (params.PARALLEL_BUILD) {
                            buildArgs << '--parallel'
                            buildArgs << "--max-workers=${Runtime.runtime.availableProcessors()}"
                        }
                        
                        // Skip tests if needed
                        if (params.RUN_UNIT_TESTS == false) {
                            buildArgs << '-x test'
                        }
                        
                        if (params.RUN_INTEGRATION_TESTS == false) {
                            buildArgs << '-x integrationTest'
                        }
                        
                        // Add any additional build arguments
                        if (params.BUILD_ARGS) {
                            buildArgs << params.BUILD_ARGS
                        }
                        
                        // Optionally skip static checks that may fail the build
                        if (params.SKIP_BUILD_STATIC_CHECKS) {
                            buildArgs << '-x checkstyleMain -x checkstyleTest'
                            buildArgs << '-x pmdMain -x pmdTest'
                            buildArgs << '-x spotbugsMain -x spotbugsTest'
                        }
                        
                        sh "./gradlew ${buildArgs.join(' ')}"
                    }
                    
                    // Archive artifacts
                    archiveArtifacts artifacts: '**/target/*.jar', allowEmptyArchive: true

                    // Create Dockerfile and build image for Trivy scan
                    sh '''
                        set -e
                        # Find the built JAR (prefer Maven target jar, exclude sources/javadoc)
                        JAR=$(find . -path '*/target/*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n1 || true)
                        if [ -z "$JAR" ]; then
                          echo "No JAR found under target directories" >&2
                          exit 1
                        fi
                        echo "Using JAR: $JAR"

                        # Normalize to app.jar at workspace root for Docker context
                        cp "$JAR" app.jar

                        # Generate Dockerfile
                        cat > Dockerfile <<'EOF'
FROM openjdk:17-ea-oraclelinux7
WORKDIR /
COPY app.jar /app.jar
EXPOSE 8080
ENTRYPOINT java -jar /app.jar
EOF

                        # Build Docker image using APP_NAME from environment
                        docker build -t ${APP_NAME}:latest .
                    '''
                }
            }
        }
        
        stage('Unit Tests') {
            when {
                expression { return params.RUN_UNIT_TESTS == true }
            }
            steps {
                script {
                    try {
                        if (params.BUILD_TOOL == 'maven') {
                            def testArgs = ['test', '-Dmaven.test.failure.ignore=true']
                            if (params.SKIP_BUILD_STATIC_CHECKS) {
                                testArgs += [
                                    '-Dcheckstyle.skip=true',
                                    '-Dpmd.skip=true',
                                    '-Dspotbugs.skip=true',
                                    '-Dcheckstyle.failOnViolation=false',
                                    '-Dpmd.failOnViolation=false',
                                    '-Dspotbugs.failOnError=false',
                                    '-Dnohttp.skip=true',
                                    '-Dnohttp.check.skip=true',
                                    '-DskipChecks',
                                    '-Dnohttp=false'
                                ]
                            }
                            sh "mvn ${testArgs.join(' ')}"
                            junit '**/target/surefire-reports/**/*.xml'
                        } else {
                            sh './gradlew test --tests "**/*Test.*" --no-daemon'
                            junit '**/test-results/test/**/*.xml'
                        }
                    } catch (err) {
                        echo "Unit tests failed: ${err.message}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            post {
                always {
                    // Generate test coverage report
                    script {
                        if (params.BUILD_TOOL == 'maven') {
                            if (fileExists('pom.xml') && fileExists('target/site/jacoco/jacoco.xml')) {
                                try {
                                    jacoco execPattern: '**/target/jacoco.exec', 
                                           classPattern: '**/target/classes', 
                                           sourcePattern: '**/src/main/java',
                                           exclusionPattern: '**/target/generated-sources/**'
                                } catch (ignored) {
                                    echo 'JaCoCo plugin not available; skipping coverage publish for Maven.'
                                }
                            }
                        } else {
                            if (fileExists('build/reports/jacoco/test/jacocoTestReport.xml')) {
                                try {
                                    jacoco execPattern: '**/build/jacoco/test.exec', 
                                           classPattern: '**/build/classes', 
                                           sourcePattern: '**/src/main/java',
                                           exclusionPattern: '**/build/generated/sources/**'
                                } catch (ignored) {
                                    echo 'JaCoCo plugin not available; skipping coverage publish for Gradle.'
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage('Integration Tests') {
            when {
                expression { return params.RUN_INTEGRATION_TESTS == true }
            }
            steps {
                script {
                    try {
                        if (params.BUILD_TOOL == 'maven') {
                            def itArgs = ['verify', '-DskipUnitTests', '-Dmaven.test.failure.ignore=true']
                            if (params.SKIP_BUILD_STATIC_CHECKS) {
                                itArgs += [
                                    '-Dcheckstyle.skip=true',
                                    '-Dpmd.skip=true',
                                    '-Dspotbugs.skip=true',
                                    '-Dcheckstyle.failOnViolation=false',
                                    '-Dpmd.failOnViolation=false',
                                    '-Dspotbugs.failOnError=false',
                                    '-Dnohttp.skip=true',
                                    '-Dnohttp.check.skip=true',
                                    '-DskipChecks',
                                    '-Dnohttp=false'
                                ]
                            }
                            sh "mvn ${itArgs.join(' ')}"
                            junit '**/target/failsafe-reports/**/*.xml'
                        } else {
                            sh './gradlew integrationTest --tests "**/*IT.*" --no-daemon'
                            junit '**/test-results/integrationTest/**/*.xml'
                        }
                    } catch (err) {
                        echo "Integration tests failed: ${err.message}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }
        
        stage('Code Quality') {
            when {
                expression { return params.RUN_CODE_ANALYSIS == true }
            }
            steps {
                script {
                    // Run PMD
                    if (params.BUILD_TOOL == 'maven') {
                        sh 'mvn pmd:pmd pmd:cpd'
                        // Archive XML reports as a fallback
                        archiveArtifacts artifacts: '**/target/pmd.xml,**/target/cpd.xml', allowEmptyArchive: true
                        // Publish HTML reports if generated via Maven Site
                        if (fileExists('target/site/pmd.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'target/site',
                                reportFiles: 'pmd.html',
                                reportName: 'PMD Report',
                                reportTitles: 'PMD'
                            ])
                        }
                        if (fileExists('target/site/cpd.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'target/site',
                                reportFiles: 'cpd.html',
                                reportName: 'CPD Report',
                                reportTitles: 'CPD'
                            ])
                        }
                    } else {
                        sh './gradlew pmdMain pmdTest cpdCheck --no-daemon'
                        // Archive XML reports as a fallback
                        archiveArtifacts artifacts: '**/build/reports/pmd/*.xml,**/build/reports/cpd/*.xml', allowEmptyArchive: true
                        // Publish HTML reports if available
                        if (fileExists('build/reports/pmd/main.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/pmd',
                                reportFiles: 'main.html',
                                reportName: 'PMD Report',
                                reportTitles: 'PMD'
                            ])
                        }
                        if (fileExists('build/reports/cpd/index.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/cpd',
                                reportFiles: 'index.html',
                                reportName: 'CPD Report',
                                reportTitles: 'CPD'
                            ])
                        }
                    }
                    
                    // Run Checkstyle (publish via HTML + archive, no plugin dependency)
                    if (params.BUILD_TOOL == 'maven') {
                        sh 'mvn checkstyle:checkstyle'
                        // Archive XML
                        archiveArtifacts artifacts: '**/target/checkstyle-result.xml', allowEmptyArchive: true
                        // Publish HTML if available (via Maven Site)
                        if (fileExists('target/site/checkstyle.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'target/site',
                                reportFiles: 'checkstyle.html',
                                reportName: 'Checkstyle Report',
                                reportTitles: 'Checkstyle'
                            ])
                        }
                    } else {
                        sh './gradlew checkstyleMain checkstyleTest --no-daemon'
                        // Archive XML
                        archiveArtifacts artifacts: '**/build/reports/checkstyle/*.xml', allowEmptyArchive: true
                        // Publish HTML if available
                        if (fileExists('build/reports/checkstyle/main.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/checkstyle',
                                reportFiles: 'main.html',
                                reportName: 'Checkstyle Report',
                                reportTitles: 'Checkstyle'
                            ])
                        }
                    }
                    
                    // Run SpotBugs (publish via HTML + archive, no plugin dependency)
                    if (params.BUILD_TOOL == 'maven') {
                        sh 'mvn com.github.spotbugs:spotbugs-maven-plugin:4.8.3.1:spotbugs -Dspotbugs.failOnError=false'
                        // Archive XML
                        archiveArtifacts artifacts: '**/target/spotbugsXml.xml', allowEmptyArchive: true
                        // Publish HTML if available (via Maven Site)
                        if (fileExists('target/site/spotbugs.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'target/site',
                                reportFiles: 'spotbugs.html',
                                reportName: 'SpotBugs Report',
                                reportTitles: 'SpotBugs'
                            ])
                        }
                    } else {
                        sh './gradlew spotbugsMain spotbugsTest --no-daemon'
                        // Archive XML
                        archiveArtifacts artifacts: '**/build/reports/spotbugs/*.xml', allowEmptyArchive: true
                        // Publish HTML if available
                        if (fileExists('build/reports/spotbugs/main.html')) {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/spotbugs',
                                reportFiles: 'main.html',
                                reportName: 'SpotBugs Report',
                                reportTitles: 'SpotBugs'
                            ])
                        }
                    }
                    

                }
            }
        }
        
        stage('Security Scan') {
            when {
                expression { return params.RUN_SECURITY_SCAN == true }
            }
            steps {
                script {
                    // OWASP Dependency-Check
                    sh "mkdir -p reports/dependency-check"
                    dependencyCheck additionalArguments: "--scan ${env.WORKSPACE} --format HTML --format XML --out ${env.WORKSPACE}/reports/dependency-check",
                                 odcInstallation: 'Dependency-Check',
                                 skipOnScmChange: false,
                                 skipOnUpstreamChange: false
                    
                    // Archive the report
                    dependencyCheckPublisher pattern: '**/reports/dependency-check/dependency-check-report.xml'
                    
                    // OWASP ZAP (start app JAR in background and scan if web resources exist)
                    if (fileExists('src/main/resources/static')) {
                        echo 'Starting application and running OWASP ZAP scan...'
                        try {
                            // Start the built JAR in background on port 8080
                            sh '''
                                set -e
                                JAR=""
                                if ls target/*.jar >/dev/null 2>&1; then
                                  JAR=$(ls -1 target/*.jar | head -n1)
                                else
                                  JAR=$(find . -path '*/target/*.jar' -maxdepth 3 | head -n1 || true)
                                fi
                                if [ -z "$JAR" ]; then
                                  echo "No JAR found in target directories" >&2
                                  exit 1
                                fi
                                echo "Launching $JAR"
                                nohup java -jar -Dserver.port=9090 "$JAR" > app.log 2>&1 &
                                echo $! > app.pid

                                echo "Waiting for app to become available on http://localhost:8080 ..."
                                for i in $(seq 1 60); do
                                  if curl -sf http://localhost:9090/ >/dev/null 2>&1; then
                                    echo "App is up"
                                    break
                                  fi
                                  sleep 2
                                done
                            '''

                            // Run ZAP in Docker and scan the app via host.docker.internal
                            sh '''
                                mkdir -p reports/zap

                                docker run --rm -u root \
                                  -v $(pwd)/reports/zap:/zap/wrk \
                                  --add-host=host.docker.internal:host-gateway \
                                  zaproxy/zap-stable:2.16.1 \
                                  zap-api-scan.py \
                                    -t http://host.docker.internal:9090/ \
                                    -f openapi \
                                    -r report.html \
                                    -d -I

                                echo "Report available at zap-reports/report.html"
                            '''
                        } finally {
                            // Cleanup ZAP and stop the app
                            sh '''
                                docker stop zap >/dev/null 2>&1 || true
                                docker rm zap >/dev/null 2>&1 || true
                                if [ -f app.pid ]; then
                                  kill $(cat app.pid) >/dev/null 2>&1 || true
                                  rm -f app.pid
                                fi
                                rm -rf $(pwd)/reports/zap
                            '''
                        }

                        // Publish ZAP report
                        publishHTML([
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'reports/zap',
                            reportFiles: 'report.html',
                            reportName: 'OWASP ZAP Report',
                            reportTitles: 'OWASP ZAP Security Scan'
                        ])
                    }
                    
                    // Run Trivy for container scanning (if building Docker image)
                    if (fileExists('Dockerfile')) {
                        sh '''
                            mkdir -p reports/trivy
                            /usr/bin/trivy image --severity CRITICAL --format template --template "@/usr/local/share/trivy/templates/html.tpl" -o reports/trivy/trivy-report.html spring-petclinic:latest || true
                        '''
                        
                        // Publish Trivy report
                        publishHTML([
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'reports/trivy',
                            reportFiles: 'trivy-report.html',
                            reportName: 'Trivy Report',
                            reportTitles: 'Trivy Security Scan'
                        ])
                    }
                }
            }
        }
        
        stage('Generate Documentation') {
            when {
                expression { return params.GENERATE_DOCS == true }
            }
            steps {
                script {
                    if (params.BUILD_TOOL == 'maven') {

                        def docArgs = []
                        if (params.SKIP_BUILD_STATIC_CHECKS) {
                            docArgs += [
                                '-Dcheckstyle.skip=true',
                                '-Dpmd.skip=true',
                                '-Dspotbugs.skip=true',
                                '-Dcheckstyle.failOnViolation=false',
                                '-Dpmd.failOnViolation=false',
                                '-Dspotbugs.failOnError=false',
                                '-Dnohttp.skip=true',
                                '-Dnohttp.check.skip=true',
                                '-DskipChecks',
                                '-Dnohttp=false'
                            ]
                        }
                        // Generate Javadoc
                        sh "mvn javadoc:javadoc ${docArgs.join(' ')}"
                        
                        // Generate API documentation (if using Spring REST Docs or similar)
                        if (fileExists('src/test/java/org/springframework/samples/petclinic/api')) {
                            sh 'mvn test -Dtest=DocumentationTest'
                            sh 'mvn spring-restdocs:generate'
                        }
                        
                        // Archive documentation
                        publishHTML([
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'target/site/apidocs',
                            reportFiles: 'index.html',
                            reportName: 'JavaDoc',
                            reportTitles: 'API Documentation'
                        ])
                        
                    } else {
                        // Gradle documentation
                        sh './gradlew javadoc --no-daemon'
                        
                        // Archive documentation
                        publishHTML([
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'build/docs/javadoc',
                            reportFiles: 'index.html',
                            reportName: 'JavaDoc',
                            reportTitles: 'API Documentation'
                        ])
                    }
                }
            }
        }
    }
    
    post {
        always {
            // Clean up workspace
            script {
                try {
                    // Use core Jenkins step to cleanup when 'cleanWs' is unavailable
                    deleteDir()
                } catch (e) {
                    echo "Workspace cleanup skipped: ${e.message}"
                }
            }
            
            // Send notifications
            script {
                def buildStatus = currentBuild.currentResult
                def color = 'good'
                
                if (buildStatus == 'FAILURE') {
                    color = 'danger'
                } else if (buildStatus == 'UNSTABLE') {
                    color = 'warning'
                }
                
                // Send Slack notification unless disabled (and guard if plugin is not installed)
                if (!params.DISABLE_NOTIFICATIONS) {
                    try {
                        def slackMsg = "Build ${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}\n" +
                                       "Branch: ${env.GIT_BRANCH}\n" +
                                       "Commit: ${env.GIT_COMMIT.take(8)}\n" +
                                       "More info at: ${env.BUILD_URL}"
                        if (params.SLACK_CHANNEL?.trim()) {
                            slackSend(channel: params.SLACK_CHANNEL, color: color, message: slackMsg)
                        } else {
                            // Use default Jenkins Slack channel if configured
                            slackSend(color: color, message: slackMsg)
                        }
                    } catch (ignored) {
                        echo 'Slack plugin not available; skipping Slack notification.'
                    }
                } else {
                    echo 'Notifications are disabled by parameter.'
                }
                
                // Archive test results
                junit allowEmptyResults: true, testResults: '**/surefire-reports/*.xml,**/failsafe-reports/*.xml,**/test-results/test/*.xml'
                

            }
        }
        
        success {
            echo 'Build completed successfully!'
        }
        
        failure {
            echo 'Build failed! See the console output for details.'
        }
        
        unstable {
            echo 'Build completed with test failures or other issues.'
        }
    }
}
