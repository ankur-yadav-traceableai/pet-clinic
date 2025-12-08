package org.example

/**
 * Handles notifications for the pipeline
 */
class Notifier implements Serializable {
    private final def script
    
    /**
     * Constructor
     */
    Notifier(script = null) {
        this.script = script ?: this
    }
    
    /**
     * Send success notification
     */
    void sendSuccess(Map config = [:], Map buildInfo = [:]) {
        if (config.enabled == false) {
            script.echo "Success notifications are disabled"
            return
        }
        
        script.echo "Sending success notifications..."
        
        try {
            def channels = config.channels ?: ['console']
            
            channels.each { channel ->
                try {
                    switch (channel.toLowerCase()) {
                        case 'email':
                            sendEmailNotification(config.email, buildInfo, 'SUCCESS')
                            break
                        case 'slack':
                            sendSlackNotification(config.slack, buildInfo, 'SUCCESS')
                            break
                        case 'teams':
                            sendTeamsNotification(config.teams, buildInfo, 'SUCCESS')
                            break
                        case 'console':
                        default:
                            script.echo "[SUCCESS] Build #${buildInfo.buildNumber} completed successfully for ${buildInfo.appName} v${buildInfo.version}"
                            break
                    }
                } catch (Exception e) {
                    script.echo "Warning: Failed to send ${channel} notification: ${e.message}"
                }
            }
            
        } catch (Exception e) {
            script.echo "Error sending success notifications: ${e.message}"
        }
    }
    
    /**
     * Send failure notification
     */
    void sendFailure(Map config = [:], Map buildInfo = [:]) {
        if (config.enabled == false) {
            script.echo "Failure notifications are disabled"
            return
        }
        
        script.echo "Sending failure notifications..."
        
        try {
            def channels = config.channels ?: ['console']
            
            channels.each { channel ->
                try {
                    switch (channel.toLowerCase()) {
                        case 'email':
                            sendEmailNotification(config.email, buildInfo, 'FAILURE')
                            break
                        case 'slack':
                            sendSlackNotification(config.slack, buildInfo, 'FAILURE')
                            break
                        case 'teams':
                            sendTeamsNotification(config.teams, buildInfo, 'FAILURE')
                            break
                        case 'console':
                        default:
                            def errorMsg = buildInfo.error ? " with error: ${buildInfo.error}" : ""
                            script.echo "[FAILURE] Build #${buildInfo.buildNumber} failed for ${buildInfo.appName}${errorMsg}"
                            break
                    }
                } catch (Exception e) {
                    script.echo "Warning: Failed to send ${channel} notification: ${e.message}"
                }
            }
            
        } catch (Exception e) {
            script.echo "Error sending failure notifications: ${e.message}"
        }
    }
    
    /**
     * Send unstable notification
     */
    void sendUnstable(Map config = [:], Map buildInfo = [:]) {
        if (config.enabled == false) {
            script.echo "Unstable build notifications are disabled"
            return
        }
        
        script.echo "Sending unstable build notifications..."
        
        try {
            def channels = config.channels ?: ['console']
            
            channels.each { channel ->
                try {
                    switch (channel.toLowerCase()) {
                        case 'email':
                            sendEmailNotification(config.email, buildInfo, 'UNSTABLE')
                            break
                        case 'slack':
                            sendSlackNotification(config.slack, buildInfo, 'UNSTABLE')
                            break
                        case 'teams':
                            sendTeamsNotification(config.teams, buildInfo, 'UNSTABLE')
                            break
                        case 'console':
                        default:
                            script.echo "[UNSTABLE] Build #${buildInfo.buildNumber} is unstable for ${buildInfo.appName} v${buildInfo.version}"
                            break
                    }
                } catch (Exception e) {
                    script.echo "Warning: Failed to send ${channel} notification: ${e.message}"
                }
            }
            
        } catch (Exception e) {
            script.echo "Error sending unstable build notifications: ${e.message}"
        }
    }
    
    /**
     * Send email notification
     */
    private void sendEmailNotification(Map config, Map buildInfo, String status) {
        if (!config || !config.recipients) {
            script.echo "Email recipients not configured"
            return
        }
        
        def subject = "[${status}] ${buildInfo.appName} v${buildInfo.version} - Build #${buildInfo.buildNumber}"
        def body = """
        <h2>Build ${status} - ${buildInfo.appName} v${buildInfo.version}</h2>
        <p><strong>Build Number:</strong> #${buildInfo.buildNumber}</p>
        <p><strong>Status:</strong> ${status}</p>
        <p><strong>Commit:</strong> ${buildInfo.commit ?: 'N/A'}</p>
        <p><strong>Branch:</strong> ${buildInfo.branch ?: 'N/A'}</p>
        """
        
        if (status == 'FAILURE' && buildInfo.error) {
            body += "<p><strong>Error:</strong> ${buildInfo.error}</p>"
        }
        
        if (buildInfo.buildUrl) {
            body += "<p><a href='${buildInfo.buildUrl}'>View Build</a></p>"
        }
        
        script.emailext(
            subject: subject,
            body: body,
            to: config.recipients,
            mimeType: 'text/html',
            recipientProviders: config.sendToIndividuals ? [[$class: 'DevelopersRecipientProvider']] : []
        )
    }
    
    /**
     * Send Slack notification
     */
    private void sendSlackNotification(Map config, Map buildInfo, String status) {
        if (!config || !config.channel) {
            script.echo "Slack channel not configured"
            return
        }
        
        def color = 'good' // green
        if (status == 'FAILURE') {
            color = 'danger' // red
        } else if (status == 'UNSTABLE') {
            color = 'warning' // yellow
        }
        
        def message = ""
        if (status == 'SUCCESS') {
            message = "✅ Build #${buildInfo.buildNumber} for ${buildInfo.appName} v${buildInfo.version} completed successfully!"
        } else if (status == 'FAILURE') {
            message = "❌ Build #${buildInfo.buildNumber} for ${buildInfo.appName} failed!"
            if (buildInfo.error) {
                message += "\nError: ${buildInfo.error}"
            }
        } else {
            message = "⚠️ Build #${buildInfo.buildNumber} for ${buildInfo.appName} v${buildInfo.version} is unstable"
        }
        
        if (buildInfo.buildUrl) {
            message += "\n<${buildInfo.buildUrl}|View Build>"
        }
        
        script.slackSend(
            channel: config.channel,
            color: color,
            message: message,
            failOnError: false
        )
    }
    
    /**
     * Send Microsoft Teams notification
     */
    private void sendTeamsNotification(Map config, Map buildInfo, String status) {
        if (!config || !config.webhookUrl) {
            script.echo "Teams webhook URL not configured"
            return
        }
        
        def themeColor = '00FF00' // green
        if (status == 'FAILURE') {
            themeColor = 'FF0000' // red
        } else if (status == 'UNSTABLE') {
            themeColor = 'FFA500' // orange
        }
        
        def facts = [
            [name: 'Project', value: buildInfo.appName],
            [name: 'Version', value: buildInfo.version ?: 'N/A'],
            [name: 'Build Number', value: "#${buildInfo.buildNumber}"],
            [name: 'Status', value: status],
            [name: 'Branch', value: buildInfo.branch ?: 'N/A'],
            [name: 'Commit', value: buildInfo.commit?.take(8) ?: 'N/A']
        ]
        
        if (status == 'FAILURE' && buildInfo.error) {
            facts << [name: 'Error', value: buildInfo.error]
        }
        
        def payload = [
            "@type": "MessageCard",
            "@context": "http://schema.org/extensions",
            "themeColor": themeColor,
            "summary": "${buildInfo.appName} Build ${status}",
            "sections": [
                [
                    "activityTitle": "${buildInfo.appName} Build ${status}",
                    "activitySubtitle": "Build #${buildInfo.buildNumber} - ${new Date()}",
                    "facts": facts,
                    "markdown": true
                ]
            ]
        ]
        
        if (buildInfo.buildUrl) {
            payload.sections[0].potentialAction = [
                [
                    "@type": "OpenUri",
                    "name": "View Build",
                    "targets": [
                        ["os": "default", "uri": buildInfo.buildUrl]
                    ]
                ]
            ]
        }
        
        script.httpRequest(
            url: config.webhookUrl,
            httpMode: 'POST',
            contentType: 'APPLICATION_JSON',
            requestBody: groovy.json.JsonOutput.toJson(payload),
            consoleLogResponseBody: false
        )
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    def notifier = new Notifier(this)
    def buildInfo = [
        appName: 'TestApp',
        version: '1.0.0',
        buildNumber: '123',
        branch: 'main',
        commit: 'a1b2c3d4',
        buildUrl: 'http://jenkins.example.com/job/test/123/'
    ]
    
    if (binding.variables.get('status') == 'success') {
        notifier.sendSuccess([enabled: true, channels: ['console']], buildInfo)
    } else if (binding.variables.get('status') == 'failure') {
        buildInfo.error = 'Test error message'
        notifier.sendFailure([enabled: true, channels: ['console']], buildInfo)
    } else {
        notifier.sendUnstable([enabled: true, channels: ['console']], buildInfo)
    }
}
