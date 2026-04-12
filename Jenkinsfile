pipeline {
    agent any

    parameters {
        choice(name: 'TARGET_ENV', choices: ['local', 'sit', 'uat'], description: 'Environment to execute against')
        choice(name: 'ORDER_FLOW', choices: [
                'New Activation Online',
                'New Activation eSIM',
                'New Activation Offline',
                'Self Activation Offline',
                'MNP Port In Online',
                'MNP Port In eSIM',
                'MNP Port In Offline',
                'Self Port In Offline',
                'MNP Port Out',
                'Transfer of Ownership Offline',
                'Transfer of Ownership Online',
                'Transfer Balance',
                'Transfer Subscription',
                'Transfer Account',
                'Configure Subscription',
                'Resume Subscription',
                'Suspend Subscription',
                'Terminate Subscription',
                'Terminate Account',
                'Delete Subscription',
                'SIM Swap Online',
                'eSIM Swap Online',
                'SIM Swap Offline',
                'Set Sharing Limits',
                'Recharge',
                'Edit SIM Alias',
                'Set Recurrence',
                'Upgrade / Downgrade',
                'Renewal',
                'Privacy Settings',
                'Set Usage Restriction',
                'Set Wallet Sharing'
        ], description: 'Which order flow should be executed? (Selection is captured and passed through only; no routing yet)')
        string(name: 'ORDER_COUNT', defaultValue: '1', description: 'How many orders should this flow create?')
    }

    environment {
        JAWWY_ENV = "${params.TARGET_ENV}"
        ORDER_FLOW = "${params.ORDER_FLOW}"
        JAWWY_API_BASE_URL = credentials('jawwy-api-base-url')
        JAWWY_UI_BASE_URL = credentials('jawwy-ui-base-url')
        JAWWY_UI_USERNAME = credentials('jawwy-ui-username')
        JAWWY_UI_PASSWORD = credentials('jawwy-ui-password')
        JAWWY_DB_URL = credentials('jawwy-db-url')
        JAWWY_DB_USER = credentials('jawwy-db-user')
        JAWWY_DB_PASSWORD = credentials('jawwy-db-password')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Run SP Batch Flow') {
            steps {
                echo "Business Flow: ${params.ORDER_FLOW} | Env: ${params.TARGET_ENV} | Order Count: ${params.ORDER_COUNT}"
                bat 'mvn -q clean test -Denv=%TARGET_ENV% -Dtest=SpBatchTest -Dorder.count=%ORDER_COUNT% -Dorder.flow=\"%ORDER_FLOW%\" -Dbatch.mode=true -Dlogback.configurationFile=src/main/resources/logback-jenkins.xml'
            }
        }

        stage('Business Summary') {
            steps {
                bat 'if exist target\\jenkins\\sp-batch-summary.md (type target\\jenkins\\sp-batch-summary.md) else (echo No batch summary found.)'
            }
        }
    }

    post {
        always {
            script {
                try {
                    archiveArtifacts artifacts: 'target/surefire-reports/**/*,target/jenkins/**/*,allure-results/**/*,logs/**/*', allowEmptyArchive: true
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                } catch (exception) {
                    echo "Skipping post-build report publishing because no workspace context is available: ${exception.message}"
                }
            }
        }
    }
}
