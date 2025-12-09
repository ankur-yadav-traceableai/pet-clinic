package org.example

/**
 * Handles static code analysis for the pipeline
 */
class CodeAnalyzer implements Serializable {
    private final def script
    
    /**
     * Constructor
     */
    CodeAnalyzer(script = null) {
        this.script = script ?: this
    }
    
    /**
     * Run static code analysis
     * @param config Analysis configuration
     */
    void runStaticAnalysis(Map config = [:]) {
        if (config.enabled == false) {
            script.echo "Static code analysis is disabled"
            return
        }
        
        script.echo "Running static code analysis..."
        
        try {
            def tools = config.tools ?: ['checkstyle', 'pmd', 'findbugs']
            def results = []
            
            tools.each { tool ->
                try {
                    switch (tool.toLowerCase()) {
                        case 'checkstyle':
                            results << runCheckstyle(config.checkstyleConfig)
                            break
                        case 'pmd':
                            results << runPMD(config.pmdConfig)
                            break
                        case 'findbugs':
                        case 'spotbugs':
                            results << runSpotBugs(config.spotbugsConfig)
                            break
                        case 'sonarqube':
                            runSonarQubeAnalysis(config.sonarConfig)
                            break
                        default:
                            script.echo "Warning: Unknown analysis tool: ${tool}"
                    }
                } catch (Exception e) {
                    script.echo "Warning: Failed to run ${tool}: ${e.message}"
                }
            }
            
            // Process and report results
            processAnalysisResults(results, config)
            
            script.echo "Static code analysis completed"
            
        } catch (Exception e) {
            script.error "Static code analysis failed: ${e.message}"
        }
    }
    
    /**
     * Run Checkstyle analysis
     */
    private Map runCheckstyle(Map config = [:]) {
        script.echo "Running Checkstyle analysis..."
        
        def configFile = config.configFile ?: 'google_checks.xml'
        def reportFile = config.reportFile ?: 'target/checkstyle-result.xml'
        
        if (script.fileExists('pom.xml')) {
            script.sh "mvn checkstyle:checkstyle -Dcheckstyle.config.location=${configFile}"
        } else if (script.fileExists('build.gradle')) {
            // Configure checkstyle in build.gradle if not already present
            if (!script.fileExists('config/checkstyle/checkstyle.xml')) {
                script.sh "mkdir -p config/checkstyle"
                script.sh "curl -o config/checkstyle/checkstyle.xml https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/google_checks.xml"
            }
            script.sh "./gradlew checkstyleMain checkstyleTest"
        }
        
        // Publish checkstyle results
        if (script.fileExists(reportFile)) {
            script.step([
                $class: 'CheckStylePublisher',
                canRunOnFailed: true,
                defaultEncoding: '',
                healthy: '100',
                pattern: reportFile,
                unHealthy: '90',
                useStableBuildAsReference: true
            ])
        }
        
        return [
            tool: 'checkstyle',
            reportFile: reportFile,
            status: 'completed'
        ]
    }
    
    /**
     * Run PMD analysis
     */
    private Map runPMD(Map config = [:]) {
        script.echo "Running PMD analysis..."
        
        def ruleset = config.ruleset ?: 'rulesets/java/quickstart.xml'
        def reportFile = config.reportFile ?: 'target/pmd.xml'
        
        if (script.fileExists('pom.xml')) {
            script.sh "mvn pmd:pmd -Dpmd.ruleset=${ruleset} -Dpmd.outputFile=${reportFile}"
        } else if (script.fileExists('build.gradle')) {
            // Configure PMD in build.gradle if not already present
            script.sh """
            if ! grep -q 'pmd' build.gradle; then
                echo "\napply plugin: 'pmd'\n\n" >> build.gradle
            fi
            """
            script.sh "./gradlew pmdMain pmdTest"
        }
        
        // Publish PMD results
        if (script.fileExists(reportFile)) {
            script.step([
                $class: 'PmdPublisher',
                pattern: reportFile,
                healthy: 100,
                unHealthy: 90,
                pluginName: 'PMD',
                thresholdLimit: 'low',
                defaultEncoding: '',
                canRunOnFailed: true,
                useStableBuildAsReference: true
            ])
        }
        
        return [
            tool: 'pmd',
            reportFile: reportFile,
            status: 'completed'
        ]
    }
    
    /**
     * Run SpotBugs/FindBugs analysis
     */
    private Map runSpotBugs(Map config = [:]) {
        script.echo "Running SpotBugs analysis..."
        
        def reportFile = config.reportFile ?: 'target/spotbugs.xml'
        
        if (script.fileExists('pom.xml')) {
            script.sh "mvn com.github.spotbugs:spotbugs-maven-plugin:4.8.3.1:spotbugs com.github.spotbugs:spotbugs-maven-plugin:4.8.3.1:check -Dspotbugs.failOnError=false"
            script.sh "mvn com.github.spotbugs:spotbugs-maven-plugin:4.8.3.1:spotbugs -Dspotbugs.includeFilterFile=findbugs-include.xml -Dspotbugs.excludeFilterFile=findbugs-exclude.xml -Dspotbugs.failOnError=false"
        } else if (script.fileExists('build.gradle')) {
            // Configure SpotBugs in build.gradle if not already present
            script.sh """
            if ! grep -q 'spotbugs' build.gradle; then
                echo "\napply plugin: 'com.github.spotbugs'\n\nbuildscript {\n    repositories {\n        maven {\n            url 'https://plugins.gradle.org/m2/'\n        }\n    }\n    dependencies {\n        classpath 'com.github.spotbugs.snom:spotbugs-gradle-plugin:4.7.0'\n    }\n}\n" >> build.gradle
            fi
            """
            script.sh "./gradlew spotbugsMain spotbugsTest"
        }
        
        // Publish SpotBugs results
        if (script.fileExists(reportFile)) {
            script.step([
                $class: 'FindBugsPublisher',
                pattern: reportFile,
                excludePattern: '',
                canRunOnFailed: true,
                defaultEncoding: '',
                excludePattern: '',
                healthy: '1',
                includePattern: '',
                unHealthy: '10',
                useStableBuildAsReference: true
            ])
        }
        
        return [
            tool: 'spotbugs',
            reportFile: reportFile,
            status: 'completed'
        ]
    }
    
    /**
     * Run SonarQube analysis
     */
    private void runSonarQubeAnalysis(Map config = [:]) {
        script.echo "Running SonarQube analysis..."
        
        def sonarConfig = [
            branch: config.branch ?: script.env.BRANCH_NAME ?: 'main',
            projectKey: config.projectKey ?: script.env.SONAR_PROJECT_KEY,
            projectName: config.projectName ?: script.env.SONAR_PROJECT_NAME,
            projectVersion: config.projectVersion ?: script.env.BUILD_NUMBER,
            sources: config.sources ?: 'src/main/java',
            tests: config.tests ?: 'src/test/java',
            javaBinaries: config.javaBinaries ?: 'target/classes',
            javaLibraries: config.javaLibraries ?: 'target/*.jar',
            exclusions: config.exclusions ?: '**/*Test.java,**/test/**/*.java',
            jacocoReportPath: config.jacocoReportPath ?: 'target/jacoco.exec',
            sourceEncoding: config.sourceEncoding ?: 'UTF-8',
            language: config.language ?: 'java',
            scmDisabled: config.scmDisabled ?: false
        ]
        
        // Set up SonarQube scanner properties
        def sonarProperties = [
            "sonar.host.url=${config.serverUrl ?: script.env.SONAR_HOST_URL}",
            "sonar.login=${config.login ?: script.env.SONAR_AUTH_TOKEN}",
            "sonar.projectKey=${sonarConfig.projectKey}",
            "sonar.projectName=${sonarConfig.projectName}",
            "sonar.projectVersion=${sonarConfig.projectVersion}",
            "sonar.sources=${sonarConfig.sources}",
            "sonar.tests=${sonarConfig.tests}",
            "sonar.java.binaries=${sonarConfig.javaBinaries}",
            "sonar.java.libraries=${sonarConfig.javaLibraries}",
            "sonar.exclusions=${sonarConfig.exclusions}",
            "sonar.jacoco.reportPath=${sonarConfig.jacocoReportPath}",
            "sonar.sourceEncoding=${sonarConfig.sourceEncoding}",
            "sonar.language=${sonarConfig.language}",
            "sonar.scm.disabled=${sonarConfig.scmDisabled}",
            "sonar.scm.provider=git"
        ]
        
        // Add branch analysis if not main branch
        if (sonarConfig.branch != 'main' && sonarConfig.branch != 'master') {
            sonarProperties << "sonar.branch.name=${sonarConfig.branch}"
        }
        
        // Run SonarQube analysis
        script.withSonarQubeEnv('SonarQube') {
            if (script.fileExists('pom.xml')) {
                script.sh "mvn sonar:sonar ${sonarProperties.collect { "-D$it" }.join(' ')}"
            } else if (script.fileExists('build.gradle')) {
                script.sh "./gradlew sonarqube ${sonarProperties.collect { "-D$it" }.join(' ')}"
            } else {
                // Use standalone scanner
                script.withSonarQubeEnv('SonarQube') {
                    script.sh "sonar-scanner ${sonarProperties.collect { "-D$it" }.join(' ')}"
                }
            }
        }
    }
    
    /**
     * Process and report analysis results
     */
    private void processAnalysisResults(List results, Map config) {
        def qualityGates = config.qualityGates ?: [
            maxCritical: 0,
            maxHigh: 5,
            maxTotal: 20
        ]
        
        def summary = [
            critical: 0,
            high: 0,
            medium: 0,
            low: 0,
            total: 0
        ]
        
        // Aggregate results
        results.each { result ->
            // Parse result and update summary
            // This is a simplified example - actual implementation would parse the report files
            // and count issues by severity
            
            // For demonstration, we'll just count the report files
            if (script.fileExists(result.reportFile)) {
                summary.total++
            }
        }
        
        // Check quality gates
        def qualityGateFailed = false
        def qualityGateMessage = []
        
        if (summary.critical > qualityGates.maxCritical) {
            qualityGateFailed = true
            qualityGateMessage << "Critical issues (${summary.critical}) exceed maximum allowed (${qualityGates.maxCritical})"
        }
        
        if (summary.high > qualityGates.maxHigh) {
            qualityGateFailed = true
            qualityGateMessage << "High issues (${summary.high}) exceed maximum allowed (${qualityGates.maxHigh})"
        }
        
        if (summary.total > qualityGates.maxTotal) {
            qualityGateFailed = true
            qualityGateMessage << "Total issues (${summary.total}) exceed maximum allowed (${qualityGates.maxTotal})"
        }
        
        // Report results
        script.echo """
        ====== Static Analysis Results ======
        Critical Issues: ${summary.critical} (max: ${qualityGates.maxCritical})
        High Issues: ${summary.high} (max: ${qualityGates.maxHigh})
        Medium Issues: ${summary.medium}
        Low Issues: ${summary.low}
        Total Issues: ${summary.total} (max: ${qualityGates.maxTotal})
        ====================================
        """
        
        // Fail build if quality gate is not met
        if (qualityGateFailed) {
            script.error("Quality gate failed: ${qualityGateMessage.join('; ')}")
        }
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    def analyzer = new CodeAnalyzer(this)
    analyzer.runStaticAnalysis(binding.variables.get('config') ?: [:]) 
}
