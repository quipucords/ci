pipeline {
    agent { label 'rhel7-os' }

environment {
    //qpc_version = getQPCVersion()
    //build_name = getBuildName()
    //image_name = "quipucords:${qpc_version}"
    //tarfile = "quipucords.${qpc_version}.tar"
    //targzfile = "${tarfile}.gz"
    install_tar = "quipucords.install.tar"
    //install_targzfile = "${install_tar}.gz"
}

parameters {
    string(defaultValue: "master", description: 'What version?', name: 'version_name')
    choice(choices: ['branch', 'tag'], description: "Branch or Tag?", name: 'version_type')
}

stages {
    stage('Build Info') {
        steps {
            echo "Version: ${params.version_name}\nVersion Type: ${params.version_type}\nCommit: ${env.GIT_COMMIT}\n\nBuild_version: ${env.build_version}"
        }
    }

    stage('Setup System') {
    	steps {
            install_deps()
            setupDocker()
        }//end steps
    }//end stage

    stage('Setup Camayoc') {
        steps {
            setup_camayoc()
        }
    }

    stage('Run Camayoc Tests') {
        steps {

        // Set home dir to workspace dir
        sh 'export XDG_CONFIG_HOME=$(pwd)'
        sh 'ls -la ~'

        dir('camayoc') {
        	sh '''\
        		#py.test -c pytest.ini -l -ra -s -vvv --junit-xml yupana-junit.xml --rootdir camayoc/camayoc/tests/qpc camayoc/camayoc/tests/qpc/yupana
        		#pipenv run pytest -vvv --junit-xml yupana-junit.xml camayoc/tests/qpc/yupana
			'''.stripIndent()
			}
        }
    }

    stage('Run Test Reports') {
    	steps{
    		dir('camayoc') {
    			//junit 'yupana-junit.xml'
    		}
    	}
    }
}
}


/////////////////////////
//// Setup Functions ////
/////////////////////////
def install_deps() {
    configFileProvider([configFile(fileId:
    '0f157b1a-7068-4c75-a672-3b1b90f97ddd', targetLocation: 'rhel7-custom.repo')]) {
	    sh 'sudo yum update -y'
        sh 'sudo yum -y install python36 python36-pip ansible'
        sh 'python3 -m pip install pipenv --user'
    }//end configfile
}//end def

def setupDocker() {
    sh """\
    echo "OPTIONS=--log-driver=journald" > docker.conf
    echo "DOCKER_CERT_PATH=/etc/docker" >> docker.conf
    echo "INSECURE_REGISTRY=\\"--insecure-registry \${DOCKER_REGISTRY}\\"" >> docker.conf
    sudo cp docker.conf /etc/sysconfig/docker
    """.stripIndent()
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
        mkdir -p ~/.config/camayoc/
        cp camayoc/pytest.ini .
    '''.stripIndent()

    // Setup Camayoc Config File
	configFileProvider([configFile(fileId: '62cf0ccc-220e-4177-9eab-f39701bff8d7', targetLocation: '/home/jenkins/.config/camayoc/config.yaml')]) {
        sh '''\
            cat ~/.config/camayoc/config.yaml
            sed -i "s/{jenkins_slave_ip}/${OPENSTACK_PUBLIC_IP}/" ~/.config/camayoc/config.yaml
        '''.stripIndent()
    }//end configfile
}//end def
