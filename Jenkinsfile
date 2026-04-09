pipeline {
    agent any

    parameters {
        choice(name: 'TARGET_ENV', choices: ['local', 'sit', 'uat'], description: 'Environment to execute against')
        string(name: 'ORDER_COUNT', defaultValue: '1', description: 'How many new activation orders should the SP flow create?')
    }

    environment {
        JAWWY_ENV = "${params.TARGET_ENV}"
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
                bat 'mvn -q clean test -Denv=%TARGET_ENV% -Dtest=SpBatchTest -Dorder.count=%ORDER_COUNT% -Dbatch.mode=true'
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
