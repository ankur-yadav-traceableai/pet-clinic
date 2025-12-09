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
                            runDependencyCheck(config?.dependencyCheck ?: [:])
                            break
                        case 'owasp-zap':
                            runOwaspZap(config?.owaspZap ?: [:])
                            break
                        case 'snyk':
                            runSnykScan(config?.snyk ?: [:])
                            break
                        case 'trivy':
                            runTrivyScan(config?.trivy ?: [:])
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
     * Parse OWASP Dependency-Check XML and return severity counts
     */
    private Map parseDependencyCheckReport(String xmlPath) {
        def counts = [critical: 0, high: 0, medium: 0, low: 0]
        try {
            def xmlText = script.readFile(xmlPath)
            def root = new groovy.util.XmlSlurper().parseText(xmlText)
            // Iterate vulnerabilities and count by severity
            root.dependencies.dependency.each { dep ->
                dep.vulnerabilities.vulnerability.each { v ->
                    def sev = (v.severity?.text() ?: '').toLowerCase()
                    switch (sev) {
                        case 'critical': counts.critical++; break
                        case 'high': counts.high++; break
                        case 'medium': counts.medium++; break
                        case 'low': counts.low++; break
                    }
                }
            }
        } catch (Exception e) {
            script.echo "Warning: Failed to parse Dependency-Check report ${xmlPath}: ${e.message}"
        }
        return counts
    }
    
    /**
     * Run OWASP Dependency-Check
     */
    private void runDependencyCheck(Map config = [:]) {
        script.echo "Running OWASP Dependency-Check..."
        
        // Mirror Jenkinsfile behavior: use Jenkins Dependency-Check plugin and publish results
        def reportDir = config?.reportDir ?: "${script.env.WORKSPACE}/reports/dependency-check"
        script.sh "mkdir -p ${reportDir}"
        
        def odcInstallation = config?.odcInstallation ?: 'Dependency-Check'
        def additionalArgs = config?.additionalArguments ?: "--scan ${script.env.WORKSPACE} --format HTML --format XML --out ${reportDir}"
        
        // Run scan using Jenkins plugin steps (same as Jenkinsfile)
        script.dependencyCheck(
            additionalArguments: additionalArgs,
            odcInstallation: odcInstallation,
            skipOnScmChange: false,
            skipOnUpstreamChange: false
        )
        
        // Publish results using the plugin's publisher
        script.dependencyCheckPublisher(pattern: '**/reports/dependency-check/dependency-check-report.xml')
        
        // Parse XML report to compute severity counts
        def dcvXmlPath = "${reportDir}/dependency-check-report.xml"
        def dcvFindings = [critical: 0, high: 0, medium: 0, low: 0]
        if (script.fileExists(dcvXmlPath)) {
            dcvFindings = parseDependencyCheckReport(dcvXmlPath)
        } else {
            script.echo "Warning: Dependency-Check XML not found at ${dcvXmlPath}; summary counts will be zero"
        }
        
        // Store results with findings
        scanResults['dependency-check'] = [
            tool: 'OWASP Dependency-Check',
            reportDir: reportDir,
            reportFile: "${reportDir}/dependency-check-report.xml",
            findings: dcvFindings,
            status: 'completed'
        ]
    }
    
    /**
     * Run OWASP ZAP scan
     */
    private void runOwaspZap(Map config = [:]) {
        script.echo "Running OWASP ZAP scan..."
        
        // Check if web resources exist (like in Jenkinsfile)
        if (!script.fileExists('src/main/resources/static')) {
            script.echo "Warning: No web resources found (src/main/resources/static), skipping OWASP ZAP scan"
            return
        }
        
        script.echo 'Starting application and running OWASP ZAP scan...'
        
        try {
            // Start the built JAR in background on port 9090
            script.sh '''
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

                echo "Waiting for app to become available on http://localhost:9090 ..."
                for i in $(seq 1 60); do
                  if curl -sf http://localhost:9090/ >/dev/null 2>&1; then
                    echo "App is up"
                    break
                  fi
                  sleep 2
                done
            '''

            // Run ZAP in Docker and scan the app via host.docker.internal
            script.sh '''
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

                echo "Report available at reports/zap/report.html"
            '''
            
            // Publish ZAP report
            script.publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'reports/zap',
                reportFiles: 'report.html',
                reportName: 'OWASP ZAP Report',
                reportTitles: 'OWASP ZAP Security Scan'
            ])
            
            // Store results
            scanResults['owasp-zap'] = [
                tool: 'OWASP ZAP',
                target: 'http://localhost:9090/',
                reportDir: 'reports/zap',
                reportFile: 'reports/zap/report.html',
                status: 'completed'
            ]
            
        } finally {
            // Cleanup ZAP and stop the app
            script.sh '''
                docker stop zap >/dev/null 2>&1 || true
                docker rm zap >/dev/null 2>&1 || true
                if [ -f app.pid ]; then
                  kill $(cat app.pid) >/dev/null 2>&1 || true
                  rm -f app.pid
                fi
                rm -rf $(pwd)/reports/zap
            '''
        }
    }
    
    /**
     * Run Snyk security scan
     */
    private void runSnykScan(Map config = [:]) {
        script.echo "Running Snyk security scan..."
        
        def snykToken = config?.token ?: script.env.SNYK_TOKEN
        if (!snykToken) {
            script.echo "Warning: Snyk token not provided. Skipping Snyk scan."
            return
        }
        
        def reportDir = config?.reportDir ?: 'target/snyk'
        def reportFile = config?.reportFile ?: 'snyk-report.html'
        
        // Install Snyk if not available
        if (!script.sh(script: 'which snyk', returnStatus: true)) {
            script.sh 'npm install -g snyk'
        }
        
        // Authenticate
        script.withCredentials([script.string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            // Run Snyk test
            script.sh "snyk auth ${snykToken}"
            
            // Test for vulnerabilities
            script.sh "snyk test --severity-threshold=${config?.severityThreshold ?: 'high'} --json > ${reportDir}/snyk-test.json || true"
            
            // Monitor project
            if (config?.monitor) {
                script.sh "snyk monitor"
            }
            
            // Generate HTML report
            script.sh "snyk test --severity-threshold=${config?.severityThreshold ?: 'high'} --file=${reportDir}/${reportFile} --format=html || true"
            
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
        
        // Check if Dockerfile exists (created during build stage)
        if (!script.fileExists('Dockerfile')) {
            script.echo "Warning: Dockerfile not found, skipping Trivy scan"
            return
        }
        
        def appName = config?.appName ?: script.env.APP_NAME ?: 'spring-petclinic'
        def imageName = "${appName}:latest"
        
        script.sh """
            mkdir -p reports/trivy
            /usr/bin/trivy image --severity CRITICAL --format template --template "@/usr/local/share/trivy/templates/html.tpl" -o reports/trivy/trivy-report.html ${imageName} || true
        """
        
        // Publish Trivy report
        script.publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'reports/trivy',
            reportFiles: 'trivy-report.html',
            reportName: 'Trivy Report',
            reportTitles: 'Trivy Security Scan'
        ])
        
        // Store results
        scanResults['trivy'] = [
            tool: 'Trivy',
            image: imageName,
            reportDir: 'reports/trivy',
            reportFile: 'reports/trivy/trivy-report.html',
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
            def scanSummary = [
                tool: result.tool,
                status: result.status,
                report: result.reportFile ?: 'N/A',
                findings: (result.findings ?: [critical: 0, high: 0, medium: 0, low: 0])
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
            low: scanResults.sum { it.value.findings?.low ?: 0 }
        ]
    }
}
