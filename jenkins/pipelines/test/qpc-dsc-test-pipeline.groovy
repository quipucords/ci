pipeline {
    agent { label "${params.node_os}" }

environment {
    install_tar = "quipucords.install.tar"
}

parameters {
    choice(choices: ['qpc', 'dsc'], description: "Project (upstream vs downstream)", name: 'project')
    choice(choices: ['rhel8-os'], description: "Node OS", name: 'node_os')
    string(defaultValue: '0.9.2', description: "Server Version", name: 'server_install_version')
    string(defaultValue: '0.9.3', description: "CLI Version", name: 'cli_install_version')
    string(defaultValue: '', description: "Optional Server Image", name: 'server_image')
}

stages {
    stage('Build Info') {
        steps {
            echo "Project: ${params.project}\nCommit: ${env.GIT_COMMIT}\n\nNode OS: ${env.node_os}\n\nServer Install Version: ${params.server_install_version}\nCLI Install Version: ${params.cli_install_version}"
            sh 'cat /etc/redhat-release'
        }
    }

    stage('Setup System') {
    	steps {
            install_deps()
            setupScanUsers()
        }//end steps
    }//end stage

    stage('Install Quipucords') {
       when {
           expression { params.project == 'qpc' }
       }// end quipucords when
       steps {
           qpc_tools_install()
       }// end quipucords steps
    }//end stage

    stage('Install Discovery') {
       when {
           expression { params.project == 'dsc' }
       }// end discovery when
       steps {
           dsc_tools_install()
       }// end discovery steps
    }//end stage

    stage('Setup Camayoc') {
        steps {
            setup_camayoc()
        }//end steps
    }//end stage
   stage('Run Camayoc CLI Tests') {
       steps {
           runCamayocTest 'cli'
       }//end steps
   }//end stage

   stage('Run Camayoc API Tests') {
       steps {
           sh 'sleep 30'
           runCamayocTest 'api'
       }//end steps
   }//end stage

   stage('Archive Logs') {
       steps {
           // Archive Server Logs
           sh "tar -cvzf ${params.project}-logs.tar.gz -C ${workspace}/server/volumes/log/ ."
           archiveArtifacts "${params.project}-logs.tar.gz"
       }//end steps
   }//end stage
}
}


/////////////////////////
//// Setup Functions ////
/////////////////////////
def install_deps() {
    configFileProvider([configFile(fileId:
    '0f157b1a-7068-4c75-a672-3b1b90f97ddd', targetLocation: 'rhel7-custom.repo')]) {
        sh 'sudo yum -y install python3 python3-pip ansible podman'
        sh 'python3 -m pip install pipenv --user'
        sh 'sudo cat /etc/containers/registries.conf'
    }//end configfile
}//end def


def qpc_tools_install() {
    echo "Install Server and CLI using qpc-tools"
    // Configure Repo
    configFileProvider([configFile(fileId:
    '5fc20406-111a-4c2c-9b4b-e055f85a226f', targetLocation: 'rhel8-dsc-custom.repo')]) {
        sh 'sudo cp rhel8-dsc-custom.repo /etc/yum.repos.d/'
        sh 'sudo dnf install -y https://github.com/quipucords/qpc-tools/releases/latest/download/qpc-tools.el8.noarch.rpm'
    }//end configfile

    sh "pwd"
    sh "ls -lah"
    sh 'sudo podman pull postgres:9.6.10'
    // Install CLI
    sh "sudo qpc-tools cli install --version ${params.cli_install_version} --home-dir ${workspace}"
    // Install Server
    sh "sudo qpc-tools server install --version ${params.server_install_version} --password qpcpassw0rd --db-password pass --home-dir ${workspace}"
}//end def

def dsc_server_install_cmd() {
    cmd = "sudo dsc-tools server install --password qpcpassw0rd --db-password pass --home-dir ${workspace} --registry-user $user --registry-password $pass --version ${params.server_install_version}"
    if ( params.server_image != '' ) {
        cmd = cmd + " --server-image-name=${params.server_image} --advanced podman_tls_verify=false"
    }
    return cmd
}

def dsc_tools_install() {
    // Configure Repo
    configFileProvider([configFile(fileId:
    '5fc20406-111a-4c2c-9b4b-e055f85a226f', targetLocation: 'rhel8-dsc-custom.repo')]) {
        sh 'sudo cp rhel8-dsc-custom.repo /etc/yum.repos.d/'
        sh 'sudo yum -y install discovery-tools'
    }//end configfile
    sh "pwd"
    sh "ls -lah"
    // Install CLI
    sh "sudo dsc-tools cli install --version ${params.cli_install_version} --home-dir ${workspace}"
    // Install Server
    withCredentials([usernamePassword(credentialsId: 'test-account', passwordVariable: 'pass', usernameVariable: 'user')]) {
        // Call the isntall command generated from dsc_server_install_cmd()
        sh dsc_server_install_cmd()
        sh "sudo podman images"
        if ( params.server_image != '' ) {
            sh "sudo podman image inspect ${params.server_image}:${params.server_install_version}"
        }
    }// end withCredentials
}//end def

def setup_camayoc() {
    // Pull and Install Camayoc
   dir('camayoc') {
    git 'https://github.com/quipucords/camayoc.git'
    sh '''\
        python3 --version
    	python3 -m pipenv run make install-dev
    '''.stripIndent()
    }//end dir

    // Set pytest file
    sh '''\
        pwd
        cp camayoc/pytest.ini .
    '''.stripIndent()

    // Run config setup playbook
	configFileProvider([configFile(fileId: '62cf0ccc-220e-4177-9eab-f39701bff8d7', targetLocation: 'camayoc-config-template.yaml')]) {
        withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
            // Define some path variables for ansible
            String isolated_fs_string = "${workspace}/server/volumes/sshkeys/"
            String ssh_keyfile_string = "${workspace}/server/volumes/sshkeys/id_rsa"

            // Run configure playbook
            sh """
                sudo ansible-playbook -vvv\
                -e config_template="${workspace}/camayoc-config-template.yaml" \
                -e config_dir="${workspace}/camayoc/camayoc/" \
                -e config_file="config.yaml" \
                -e server_ip="${OPENSTACK_PUBLIC_IP}" \
                -e container_ssh_file=/sshkeys/id_rsa \
                -e isolated_fs="${isolated_fs_string}" \
                -e sshkeyfile="${ID_JENKINS_RSA}" \
                -e volume_sshkeyfile="${ssh_keyfile_string}" \
                -e isolated_fs_user=jenkins camayoc/scripts/configure-camayoc.yaml
            """.stripIndent()
        }//end withCredentials
    }//end configFileProvider
}//end def


def setupScanUsers() {
    dir('ci') {
        git 'https://github.com/quipucords/ci.git'
    }//end dir

    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
        withCredentials([file(credentialsId: '50dc19ce-555f-422c-af38-3b5ede422bb4', variable: 'ID_JENKINS_RSA_PUB')]) {
            sh 'sudo yum -y install ansible'

            sh '''\
                cat > jenkins-slave-hosts <<EOF
                [jenkins-slave]
                ${OPENSTACK_PUBLIC_IP}

                [jenkins-slave:vars]
                ansible_user=jenkins
                ansible_ssh_extra_args=-o StrictHostKeyChecking=no
                ssh_public_key_file=$(cat ${ID_JENKINS_RSA_PUB})
                EOF
            '''.stripIndent()

            sh 'ansible-playbook -b -i jenkins-slave-hosts ci/ansible/sonar-setup-scan-users.yaml'
        }//end withCredentials
    }//end sshagent
}//end def


////////////////////
// Test Functions //
////////////////////
def runCamayocTest(testset) {
    echo "Running ${testset} Tests"

    sh 'ls -lah'
    sh 'pwd'
    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
        dir('camayoc') {
        sh 'echo $CAMAYOC_CLIENT_CMD'
        sh 'ps aux | grep postgres'
        sh """
            export CAMAYOC_CLIENT_CMD='${params.project}'
            set +e
            export XDG_CONFIG_HOME=\$(pwd)
            echo \$XDG_CONFIG_HOME
            cat \$XDG_CONFIG_HOME/camayoc/config.yaml
            python3 -m pipenv run py.test -c pytest.ini -l -ra -s -vvv --junit-xml ${params.project}-$testset-junit.xml --rootdir camayoc/tests/qpc camayoc/tests/qpc/$testset
            set -e
            # tar -cvzf test-$testset-logs.tar.gz log
        """.stripIndent()
        sh 'ls -la'
        echo "${params.project}-$testset-junit.xml"
        sh "cat ${params.project}-$testset-junit.xml"

        // Archive and Load Test Results
        archiveArtifacts "${params.project}-$testset-junit.xml"
        junit "${params.project}-$testset-junit.xml"
        }//end dir
    }//end sshagent
    //archiveArtifacts "test-$testset-logs.tar.gz"
}

def runCamayocUITest(browser) {
    echo "Running ${browser} Tests"

    sh 'ls -lah'
    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
        dir('camayoc') {
            sh "sudo docker run --net='host' -d -p 4444:4444 -v /dev/shm:/dev/shm:z -v /tmp:/tmp:z selenium/standalone-$browser"
            sleep 3

            sh """\
                set +e
                export XDG_CONFIG_HOME=\$(pwd)
                echo \$XDG_CONFIG_HOME
                export SELENIUM_DRIVER=$browser
                cat \$XDG_CONFIG_HOME/camayoc/config.yaml
                python3 -m pipenv run py.test -c pytest.ini -l -ra -vvv --junit-prefix $browser --junit-xml ui-$browser-junit.xml --rootdir camayoc/tests/qpc camayoc/tests/qpc/ui
                set -e
                # tar -cvzf test-ui-$browser-logs.tar.gz log
                # sudo rm -rf log
            """.stripIndent()

            echo 'Archiving artifacts'

            //archiveArtifacts "test-ui-$browser-logs.tar.gz"
            junit "ui-$browser-junit.xml"
        }//end dir
    }//end sshagent
}//end def

