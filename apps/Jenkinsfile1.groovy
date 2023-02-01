pipeline {
    agent any
    parameters {
        choice(
            name: 'BRANCH_NAME',
            choices: [],
            description: 'Choose the branch to build'
        )
    }
    stages {
        stage('Get branches') {
            steps {
                script {
                    def branches = sh(script: "git ls-remote --heads <your_github_repo_url>", returnStdout: true).split("\n")
                    def availableBranches = branches.collect { it.split("/")[-1].trim() }
                    availableBranches.sort()
                    availableBranches.each { println "Branch: $it" }
                    params.BRANCH_NAME.choices = availableBranches
                }
            }
        }
        stage('Checkout code') {
            steps {
                checkout([$class: 'GitSCM', branches: [[name: "*/${params.BRANCH_NAME}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '<your_github_credentials_id>', url: '<your_github_repo_url>']]])
            }
        }
        stage('Retrieve Docker registry credentials from Vault') {
            steps {
                withCredentials([usernamePassword(credentialsId: '<your_vault_credentials_id>', passwordVariable: 'VAULT_PASSWORD', usernameVariable: 'VAULT_USERNAME')]) {
                    def secrets = sh(script: "vault kv get -field=value <your_vault_kv_secret_path>/docker_registry_password", returnStdout: true, env: [
                        VAULT_ADDR: 'https://vault.lazarev.gq',
                        VAULT_USERNAME: env.VAULT_USERNAME,
                        VAULT_PASSWORD: env.VAULT_PASSWORD
                    ]).trim()
                    env.NEXUS_PASSWORD = secrets
                }
            }
        }
        stage('Build Docker image') {
            steps {
                sh "docker build -t <your_image_name>:${params.BRANCH_NAME}-${env.BUILD_NUMBER} ."
            }
        }
        stage('Push Docker image') {
            steps {
                sh "docker login -u <your_nexus_username> -p $NEXUS_PASSWORD nexus.lazarev.gq"
                sh "docker tag <your_image_name>:${params.BRANCH_NAME}-${env.BUILD_NUMBER} nexus.lazarev.gq/<your_image_name>:${params.BRANCH_NAME}-${env.BUILD_NUMBER}"
                sh "docker push nexus.lazarev.gq/<your_image_name>:${params.BRANCH_NAME}-${env.BUILD_NUMBER}"
            }
        }
        stage('Deploy') {
            steps {
                // Add your deployment steps here, e.g.
                // sh 'kubectl rollout restart
