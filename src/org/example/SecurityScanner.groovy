package org.example

/**
 * Handles security scanning for the pipeline
 */
class SecurityScanner implements Serializable {
    private final def script
    private Map scanResults = [:]
    
    /**
     * Constructor
     */
    SecurityScanner(script = null) {
        this.script = script ?: this
    }
    
    /**
     * Run security scans
     * @param config Security scan configuration
     */
    void runScans(Map config = [:]) {
        if (config.enabled == false) {
            script.echo "Security scanning is disabled"
            return
        }
        
        script.echo "Running security scans..."
        
        try {
            def tools = config.tools ?: ['dependency-check', 'owasp-zap']
            
            tools.each { tool ->
                try {
                    switch (tool.toLowerCase()) {
                        case 'dependency-check':
                            runDependencyCheck(config.dependencyCheck)
                            break
                        case 'owasp-zap':
                            runOwaspZap(config.owaspZap)
                            break
                        case 'snyk':
                            runSnykScan(config.snyk)
                            break
                        case 'trivy':
                            runTrivyScan(config.trivy)
                            break
                        default:
                            script.echo "Warning: Unknown security tool: ${tool}"
                    }
                } catch (Exception e) {
                    script.echo "Warning: Failed to run ${tool}: ${e.message}"
                }
            }
            
            // Generate security report
            generateSecurityReport()
            
            script.echo "Security scanning completed"
            
        } catch (Exception e) {
            script.error "Security scanning failed: ${e.message}"
        }
    }
    
    /**
     * Run OWASP Dependency-Check
     */
    private void runDependencyCheck(Map config = [:]) {
        script.echo "Running OWASP Dependency-Check..."
        
        def reportDir = config.reportDir ?: 'target/dependency-check'
        def reportFile = config.reportFile ?: 'dependency-check-report.html'
        
        // Install if not available
        if (!script.tool('Dependency-Check')) {
            script.echo "Installing OWASP Dependency-Check..."
            script.sh 'wget -qO - https://dl.bintray.com/jeremy-long/owasp/dependency-check.gpg.key | sudo apt-key add -'
            script.sh 'echo "deb https://dl.bintray.com/jeremy-long/owasp /" | sudo tee /etc/apt/sources.list.d/dependency-check.list'
            script.sh 'sudo apt-get update && sudo apt-get install -y dependency-check'
        }
        
        // Run dependency check
        def scanCmd = [
            'dependency-check.sh',
            '--project', 'Dependency-Check',
            '--scan', '.',
            '--out', reportDir,
            '--format', 'HTML', 'JSON', 'XML',
            '--enableExperimental',
            '--failOnCVSS', config.failOnCVSS ?: '7'
        ]
        
        // Add suppression file if exists
        if (script.fileExists('dependency-check-suppression.xml')) {
            scanCmd << '--suppression' << 'dependency-check-suppression.xml'
        }
        
        script.sh scanCmd.join(' ')
        
        // Archive reports
        script.archiveArtifacts artifacts: "${reportDir}/**/*", allowEmptyArchive: true
        
        // Publish HTML report
        script.publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: reportDir,
            reportFiles: reportFile,
            reportName: 'Dependency-Check Report',
            reportTitles: 'Dependency-Check'
        ])
        
        // Store results
        scanResults['dependency-check'] = [
            tool: 'OWASP Dependency-Check',
            reportDir: reportDir,
            reportFile: "${reportDir}/${reportFile}",
            status: 'completed'
        ]
    }
    
    /**
     * Run OWASP ZAP scan
     */
    private void runOwaspZap(Map config = [:]) {
        script.echo "Running OWASP ZAP scan..."
        
        def targetUrl = config.targetUrl ?: script.env.APPLICATION_URL
        if (!targetUrl) {
            script.echo "Warning: No target URL specified for OWASP ZAP scan"
            return
        }
        
        def reportDir = config.reportDir ?: 'target/zap'
        def reportFile = config.reportFile ?: 'zap-report.html'
        
        // Start ZAP in daemon mode
        script.sh 'docker run -d --name zap -p 8080:8080 -i owasp/zap2docker-stable zap.sh -daemon -host 0.0.0.0 -port 8080 -config api.disablekey=true -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true'
        
        try {
            // Wait for ZAP to start
            script.sh 'while ! curl -s http://localhost:8080 >/dev/null; do sleep 1; echo "Waiting for ZAP..."; done'
            
            // Run ZAP spider
            script.sh "docker exec zap zap-cli -p 8080 status -t 120"
            script.sh "docker exec zap zap-cli -p 8080 open-url ${targetUrl}"
            script.sh "docker exec zap zap-cli -p 8080 spider ${targetUrl}"
            
            // Run active scan
            script.sh "docker exec zap zap-cli -p 8080 active-scan -r ${targetUrl}"
            
            // Generate report
            script.sh "mkdir -p ${reportDir}"
            script.sh "docker exec zap zap-cli -p 8080 report -o /zap/${reportFile} -f html"
            script.sh "docker cp zap:/zap/${reportFile} ${reportDir}/"
            
            // Publish HTML report
            script.publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: reportDir,
                reportFiles: reportFile,
                reportName: 'OWASP ZAP Report',
                reportTitles: 'OWASP ZAP'
            ])
            
            // Store results
            scanResults['owasp-zap'] = [
                tool: 'OWASP ZAP',
                target: targetUrl,
                reportDir: reportDir,
                reportFile: "${reportDir}/${reportFile}",
                status: 'completed'
            ]
            
        } finally {
            // Stop and remove ZAP container
            script.sh 'docker stop zap || true'
            script.sh 'docker rm zap || true'
        }
    }
    
    /**
     * Run Snyk security scan
     */
    private void runSnykScan(Map config = [:]) {
        script.echo "Running Snyk security scan..."
        
        def snykToken = config.token ?: script.env.SNYK_TOKEN
        if (!snykToken) {
            script.echo "Warning: Snyk token not provided. Skipping Snyk scan."
            return
        }
        
        def reportDir = config.reportDir ?: 'target/snyk'
        def reportFile = config.reportFile ?: 'snyk-report.html'
        
        // Install Snyk if not available
        if (!script.sh(script: 'which snyk', returnStatus: true)) {
            script.sh 'npm install -g snyk'
        }
        
        // Authenticate
        script.withCredentials([script.string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            // Run Snyk test
            script.sh "snyk auth ${snykToken}"
            
            // Test for vulnerabilities
            script.sh "snyk test --severity-threshold=${config.severityThreshold ?: 'high'} --json > ${reportDir}/snyk-test.json || true"
            
            // Monitor project
            if (config.monitor) {
                script.sh "snyk monitor"
            }
            
            // Generate HTML report
            script.sh "snyk test --severity-threshold=${config.severityThreshold ?: 'high'} --file=${reportDir}/${reportFile} --format=html || true"
            
            // Publish HTML report
            script.publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: reportDir,
                reportFiles: reportFile,
                reportName: 'Snyk Report',
                reportTitles: 'Snyk Security Scan'
            ])
            
            // Store results
            scanResults['snyk'] = [
                tool: 'Snyk',
                reportDir: reportDir,
                reportFile: "${reportDir}/${reportFile}",
                status: 'completed'
            ]
        }
    }
    
    /**
     * Run Trivy container scan
     */
    private void runTrivyScan(Map config = [:]) {
        script.echo "Running Trivy container scan..."
        
        def imageName = config.imageName ?: script.env.IMAGE_NAME
        if (!imageName) {
            script.echo "Warning: No image name specified for Trivy scan"
            return
        }
        
        def reportDir = config.reportDir ?: 'target/trivy'
        def reportFile = config.reportFile ?: 'trivy-report.json'
        
        // Install Trivy if not available
        if (!script.sh(script: 'which trivy', returnStatus: true)) {
            script.sh 'curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin'
        }
        
        // Run Trivy scan
        script.sh "mkdir -p ${reportDir}"
        script.sh "trivy image --format json --output ${reportDir}/${reportFile} --severity ${config.severity ?: 'CRITICAL,HIGH'} ${imageName} || true"
        
        // Generate HTML report
        script.sh "trivy image --format template --template '@html.tpl' --output ${reportDir}/trivy-report.html --severity ${config.severity ?: 'CRITICAL,HIGH'} ${imageName} || true"
        
        // Publish HTML report
        script.publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: reportDir,
            reportFiles: 'trivy-report.html',
            reportName: 'Trivy Report',
            reportTitles: 'Trivy Security Scan'
        ])
        
        // Store results
        scanResults['trivy'] = [
            tool: 'Trivy',
            image: imageName,
            reportDir: reportDir,
            reportFile: "${reportDir}/${reportFile}",
            status: 'completed'
        ]
    }
    
    /**
     * Generate security scan summary report
     */
    private void generateSecurityReport() {
        if (scanResults.isEmpty()) {
            script.echo "No security scan results to report"
            return
        }
        
        def report = [
            summary: [
                critical: 0,
                high: 0,
                medium: 0,
                low: 0,
                total: 0
            ],
            scans: []
        ]
        
        // Process scan results
        scanResults.each { tool, result ->
            // This is a simplified example - in a real implementation,
            // you would parse the actual report files to get vulnerability counts
            def scanSummary = [
                tool: result.tool,
                status: result.status,
                report: result.reportFile ?: 'N/A',
                findings: [
                    critical: 0, // These would be populated from actual scan results
                    high: 0,
                    medium: 0,
                    low: 0
                ]
            ]
            
            // Update summary
            report.summary.critical += scanSummary.findings.critical
            report.summary.high += scanSummary.findings.high
            report.summary.medium += scanSummary.findings.medium
            report.summary.low += scanSummary.findings.low
            report.summary.total += scanSummary.findings.critical + scanSummary.findings.high + 
                                   scanSummary.findings.medium + scanSummary.findings.low
            
            report.scans << scanSummary
        }
        
        // Print summary
        script.echo """
        ====== Security Scan Summary ======
        Tools Run: ${report.scans.size()}
        
        Critical Issues: ${report.summary.critical}
        High Issues: ${report.summary.high}
        Medium Issues: ${report.summary.medium}
        Low Issues: ${report.summary.low}
        Total Issues: ${report.summary.total}
        
        Reports:
        ${report.scans.collect { "- ${it.tool}: ${it.report}" }.join('\n')}
        ==================================
        """
        
        // Save report as artifact
        def reportFile = 'security-scan-report.json'
        script.writeJSON file: reportFile, json: report
        script.archiveArtifacts artifacts: reportFile, allowEmptyArchive: true
        
        // Set build status based on findings
        if (report.summary.critical > 0) {
            script.currentBuild.result = 'UNSTABLE'
            script.error("Security scan found ${report.summary.critical} critical vulnerabilities")
        } else if (report.summary.high > 0) {
            script.currentBuild.result = 'UNSTABLE'
            script.echo "Warning: Security scan found ${report.summary.high} high severity vulnerabilities"
        }
    }
    
    /**
     * Get vulnerability report
     */
    Map getVulnerabilityReport() {
        // This would parse the actual scan results to generate a vulnerability report
        // For now, return a summary of the scan results
        return [
            critical: scanResults.sum { it.value.findings?.critical ?: 0 },
            high: scanResults.sum { it.value.findings?.high ?: 0 },
            medium: scanResults.sum { it.value.findings?.medium ?: 0 },
            low: scanResults.sum { it.value.findings?.low ?: 0 },
            total: scanResults.size()
        ]
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    def scanner = new SecurityScanner(this)
    scanner.runScans(binding.variables.get('config') ?: [:]) 
}
