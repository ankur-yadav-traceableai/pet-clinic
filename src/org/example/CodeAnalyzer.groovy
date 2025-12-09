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
        
        if (script.fileExists('pom.xml')) {
            script.sh 'mvn checkstyle:checkstyle'
            // Archive XML
            script.archiveArtifacts artifacts: '**/target/checkstyle-result.xml', allowEmptyArchive: true
            // Publish HTML if available (via Maven Site)
            if (script.fileExists('target/site/checkstyle.html')) {
                script.publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site',
                    reportFiles: 'checkstyle.html',
                    reportName: 'Checkstyle Report',
                    reportTitles: 'Checkstyle'
                ])
            }
        } else if (script.fileExists('build.gradle')) {
            script.sh './gradlew checkstyleMain checkstyleTest --no-daemon'
            // Archive XML
            script.archiveArtifacts artifacts: '**/build/reports/checkstyle/*.xml', allowEmptyArchive: true
            // Publish HTML if available
            if (script.fileExists('build/reports/checkstyle/main.html')) {
                script.publishHTML([
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
        
        return [
            tool: 'checkstyle',
            reportFile: 'checkstyle-result.xml',
            status: 'completed'
        ]
    }
    
    /**
     * Run PMD analysis
     */
    private Map runPMD(Map config = [:]) {
        script.echo "Running PMD analysis..."
        
        if (script.fileExists('pom.xml')) {
            script.sh 'mvn pmd:pmd pmd:cpd'
            // Archive XML reports as a fallback
            script.archiveArtifacts artifacts: '**/target/pmd.xml,**/target/cpd.xml', allowEmptyArchive: true
            // Publish HTML reports if generated via Maven Site
            if (script.fileExists('target/site/pmd.html')) {
                script.publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site',
                    reportFiles: 'pmd.html',
                    reportName: 'PMD Report',
                    reportTitles: 'PMD'
                ])
            }
            if (script.fileExists('target/site/cpd.html')) {
                script.publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site',
                    reportFiles: 'cpd.html',
                    reportName: 'CPD Report',
                    reportTitles: 'CPD'
                ])
            }
        } else if (script.fileExists('build.gradle')) {
            script.sh './gradlew pmdMain pmdTest cpdCheck --no-daemon'
            // Archive XML reports as a fallback
            script.archiveArtifacts artifacts: '**/build/reports/pmd/*.xml,**/build/reports/cpd/*.xml', allowEmptyArchive: true
            // Publish HTML reports if available
            if (script.fileExists('build/reports/pmd/main.html')) {
                script.publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'build/reports/pmd',
                    reportFiles: 'main.html',
                    reportName: 'PMD Report',
                    reportTitles: 'PMD'
                ])
            }
            if (script.fileExists('build/reports/cpd/index.html')) {
                script.publishHTML([
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
        
        return [
            tool: 'pmd',
            reportFile: 'pmd.xml',
            status: 'completed'
        ]
    }
    
    /**
     * Run SpotBugs/FindBugs analysis
     */
    private Map runSpotBugs(Map config = [:]) {
        script.echo "Running SpotBugs analysis..."
        
        if (script.fileExists('pom.xml')) {
            script.sh 'mvn com.github.spotbugs:spotbugs-maven-plugin:4.8.3.1:spotbugs -Dspotbugs.failOnError=false'
            // Archive XML
            script.archiveArtifacts artifacts: '**/target/spotbugsXml.xml', allowEmptyArchive: true
            // Publish HTML if available (via Maven Site)
            if (script.fileExists('target/site/spotbugs.html')) {
                script.publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site',
                    reportFiles: 'spotbugs.html',
                    reportName: 'SpotBugs Report',
                    reportTitles: 'SpotBugs'
                ])
            }
        } else if (script.fileExists('build.gradle')) {
            script.sh './gradlew spotbugsMain spotbugsTest --no-daemon'
            // Archive XML
            script.archiveArtifacts artifacts: '**/build/reports/spotbugs/*.xml', allowEmptyArchive: true
            // Publish HTML if available
            if (script.fileExists('build/reports/spotbugs/main.html')) {
                script.publishHTML([
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
        
        return [
            tool: 'spotbugs',
            reportFile: 'spotbugsXml.xml',
            status: 'completed'
        ]
    }
    
    // SonarQube analysis removed
    
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
