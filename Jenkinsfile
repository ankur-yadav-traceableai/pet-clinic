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
        
        // SonarQube configuration
        SONAR_HOST_URL = 'https://sonarcloud.io'
        SONAR_ORGANIZATION = 'your-org'  // Update with your SonarCloud organization
        
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
                            sh 'mvn test -Dmaven.test.failure.ignore=true'
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
                                jacoco execPattern: '**/target/jacoco.exec', 
                                      classPattern: '**/target/classes', 
                                      sourcePattern: '**/src/main/java',
                                      exclusionPattern: '**/target/generated-sources/**'
                            }
                        } else {
                            if (fileExists('build/reports/jacoco/test/jacocoTestReport.xml')) {
                                jacoco execPattern: '**/build/jacoco/test.exec', 
                                      classPattern: '**/build/classes', 
                                      sourcePattern: '**/src/main/java',
                                      exclusionPattern: '**/build/generated/sources/**'
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
                            sh 'mvn verify -DskipUnitTests -Dmaven.test.failure.ignore=true'
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
                        pmd canRunOnFailed: true, 
                            pattern: '**/target/pmd.xml',
                            reportFormat: 'xml',
                            ruleSetFiles: 'pmd-ruleset.xml'
                    } else {
                        sh './gradlew pmdMain pmdTest cpdCheck --no-daemon'
                        pmd canRunOnFailed: true, 
                            pattern: '**/build/reports/pmd/*.xml',
                            reportFormat: 'xml'
                    }
                    
                    // Run Checkstyle
                    if (params.BUILD_TOOL == 'maven') {
                        sh 'mvn checkstyle:checkstyle'
                        checkstyle canRunOnFailed: true, 
                                 defaultEncoding: 'UTF-8',
                                 pattern: '**/target/checkstyle-result.xml'
                    } else {
                        sh './gradlew checkstyleMain checkstyleTest --no-daemon'
                        checkstyle canRunOnFailed: true, 
                                 defaultEncoding: 'UTF-8',
                                 pattern: '**/build/reports/checkstyle/*.xml'
                    }
                    
                    // Run SpotBugs (free alternative to FindBugs)
                    if (params.BUILD_TOOL == 'maven') {
                        sh 'mvn spotbugs:spotbugs'
                        recordIssues(
                            enabledForFailure: true,
                            tool: spotBugs(pattern: '**/target/spotbugsXml.xml')
                        )
                    } else {
                        sh './gradlew spotbugsMain spotbugsTest --no-daemon'
                        recordIssues(
                            enabledForFailure: true,
                            tool: spotBugs(pattern: '**/build/reports/spotbugs/*.xml')
                        )
                    }
                    
                    // SonarQube analysis (requires SonarQube server)
                    withSonarQubeEnv('SonarQube') {
                        if (params.BUILD_TOOL == 'maven') {
                            sh 'mvn sonar:sonar ' +
                               '-Dsonar.projectKey=spring-petclinic ' +
                               "-Dsonar.projectName=${env.APP_NAME} " +
                               "-Dsonar.projectVersion=${env.APP_VERSION} " +
                               "-Dsonar.sources=src/main/java " +
                               "-Dsonar.tests=src/test/java " +
                               "-Dsonar.java.binaries=target/classes " +
                               "-Dsonar.java.libraries=target/*.jar " +
                               "-Dsonar.junit.reportPaths=target/surefire-reports,target/failsafe-reports " +
                               "-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml"
                        } else {
                            sh "./gradlew sonarqube --no-daemon " +
                               "-Dsonar.projectKey=spring-petclinic " +
                               "-Dsonar.projectName=${env.APP_NAME} " +
                               "-Dsonar.projectVersion=${env.APP_VERSION} " +
                               "-Dsonar.sources=src/main/java " +
                               "-Dsonar.tests=src/test/java " +
                               "-Dsonar.java.binaries=build/classes " +
                               "-Dsonar.java.libraries=build/libs/*.jar " +
                               "-Dsonar.junit.reportPaths=build/test-results/test,build/test-results/integrationTest " +
                               "-Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml"
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
                    dependencyCheck additionalArguments: "--scan ${env.WORKSPACE} --format HTML --format XML --out ${env.WORKSPACE}/reports/dependency-check", 
                                 odcInstallation: 'Dependency-Check',
                                 skipOnScmChange: false,
                                 skipOnUpstreamChange: false
                    
                    // Archive the report
                    dependencyCheckPublisher pattern: '**/reports/dependency-check/dependency-check-report.xml'
                    
                    // OWASP ZAP (if a web application is running)
                    if (fileExists('src/main/resources/static')) {
                        echo 'Running OWASP ZAP scan...'
                        sh '''
                            # Start ZAP in daemon mode
                            docker run -d --name zap -p 8080:8080 -i owasp/zap2docker-stable zap.sh -daemon -host 0.0.0.0 -port 8080 -config api.disablekey=true
                            
                            # Wait for ZAP to start
                            while ! curl -s http://localhost:8080 >/dev/null; do 
                                sleep 1
                                echo "Waiting for ZAP..."
                            done
                            
                            # Run a quick scan (replace localhost:8080 with your app's URL)
                            docker exec zap zap-cli -p 8080 quick-scan -s all -r http://localhost:8080/
                            
                            # Generate report
                            mkdir -p reports/zap
                            docker exec zap zap-cli -p 8080 report -o /zap/report.html -f html
                            docker cp zap:/zap/report.html reports/zap/
                            
                            # Stop and remove container
                            docker stop zap || true
                            docker rm zap || true
                        '''
                        
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
                            trivy image --severity CRITICAL --format template --template "@/usr/local/share/trivy/templates/html.tpl" -o reports/trivy/trivy-report.html spring-petclinic:latest || true
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
                        // Generate Javadoc
                        sh 'mvn javadoc:javadoc'
                        
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
                
                // Send Slack notification
                slackSend(
                    color: color,
                    message: "Build ${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}\n" +
                             "Branch: ${env.GIT_BRANCH}\n" +
                             "Commit: ${env.GIT_COMMIT.take(8)}\n" +
                             "More info at: ${env.BUILD_URL}"
                )
                
                // Archive test results
                junit allowEmptyResults: true, testResults: '**/surefire-reports/*.xml,**/failsafe-reports/*.xml,**/test-results/test/*.xml'
                
                // Archive artifacts
                archiveArtifacts artifacts: '**/target/*.jar,**/build/libs/*.jar', allowEmptyArchive: true
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
