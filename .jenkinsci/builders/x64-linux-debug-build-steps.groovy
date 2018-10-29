#!/usr/bin/env groovy

def utils = load '.jenkinsci/utils/utils.groovy'

def developDockerManifestPush(dockerImageObj, environment) {
  manifest = load ".jenkinsci/utils/docker-manifest.groovy"
  platform = sh(script: 'uname -m', returnStdout: true).trim()
  withEnv(environment) {
    manifest.manifestCreate("${env.DOCKER_REGISTRY_BASENAME}:develop-build",
      ["${env.DOCKER_REGISTRY_BASENAME}:x86_64-develop-build",
       "${env.DOCKER_REGISTRY_BASENAME}:armv7l-develop-build",
       "${env.DOCKER_REGISTRY_BASENAME}:aarch64-develop-build"])
    manifest.manifestAnnotate("${env.DOCKER_REGISTRY_BASENAME}:develop-build",
      [
        [manifest: "${env.DOCKER_REGISTRY_BASENAME}:x86_64-develop-build",
         arch: 'amd64', os: 'linux', osfeatures: [], variant: ''],
        [manifest: "${env.DOCKER_REGISTRY_BASENAME}:armv7l-develop-build",
         arch: 'arm', os: 'linux', osfeatures: [], variant: 'v7'],
        [manifest: "${env.DOCKER_REGISTRY_BASENAME}:aarch64-develop-build",
         arch: 'arm64', os: 'linux', osfeatures: [], variant: '']
      ])
    withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', usernameVariable: 'login', passwordVariable: 'password')]) {
      manifest.manifestPush("${env.DOCKER_REGISTRY_BASENAME}:develop-build", login, password)
    }
  }
}

def initialCoverage(buildDir) {
  sh "cmake --build ${buildDir} --target coverage.init.info"
}

def postCoverage(buildDir) {
  sh "cmake --build ${buildDir} --target coverage.info"
  sh "python /tmp/lcov_cobertura.py ${buildDir}/reports/coverage.info -o ${buildDir}/reports/coverage.xml"
  cobertura autoUpdateHealth: false, autoUpdateStability: false,
    coberturaReportFile: "**/${buildDir}/reports/coverage.xml", conditionalCoverageTargets: '75, 50, 0',
    failUnhealthy: false, failUnstable: false, lineCoverageTargets: '75, 50, 0', maxNumberOfBuilds: 50,
    methodCoverageTargets: '75, 50, 0', onlyStable: false, zoomCoverageChart: false
}

def testSteps(buildDir, environment) {
  withEnv(environment) {
    sh "cd ${buildDir}; ctest --output-on-failure --no-compress-output -T Test || true"
    sh 'python .jenkinsci/helpers/platform_tag.py "Linux \$(uname -m)" \$(ls ${buildDir}/Testing/*/Test.xml)'
    // Mark build as UNSTABLE if there are any failed tests (threshold <100%)
    xunit testTimeMargin: '3000', thresholdMode: 2, thresholds: [passed(unstableThreshold: '100')], \
      tools: [CTest(deleteOutputFiles: true, failIfNotNew: false, \
      pattern: "${buildDir}/Testing/**/Test.xml", skipNoTestFiles: false, stopProcessingIfError: true)]
  }
}

def buildSteps(String nodeLabel, int parallelism, String compilerVersion,
      boolean pushDockerTag, boolean coverage, boolean testing, boolean cppcheck, boolean sonar, List environment) {
  node (nodeLabel) {
    stage('Build') {
      withEnv(environment) {
        scmVars = checkout scm
        build = load '.jenkinsci/build.groovy'
        vars = load ".jenkinsci/utils/vars.groovy"
        utils = load ".jenkinsci/utils/utils.groovy"
        dockerUtils = load ".jenkinsci/utils/docker-pull-or-build.groovy"
        buildDir = 'build'
        compilers = vars.compilerMapping()
        cmakeBooleanOption = [ (true): 'ON', (false): 'OFF' ]
        platform = sh(script: 'uname -m', returnStdout: true).trim()
        sh "docker network create ${env.IROHA_NETWORK}"
        // iC = dockerUtils.dockerPullOrBuild("${platform}-develop-build",
        //      "${env.GIT_RAW_BASE_URL}/${scmVars.GIT_COMMIT}/docker/develop/Dockerfile",
        //      "${env.GIT_RAW_BASE_URL}/${utils.previousCommitOrCurrent(scmVars)}/docker/develop/Dockerfile",
        //      "${env.GIT_RAW_BASE_URL}/dev/docker/develop/Dockerfile",
        //      scmVars,
        //      environment,
        //      ['PARALLELISM': parallelism])
        iC = dockerUtils.dockerPullOrBuild("${platform}-develop-build",
             "${env.GIT_RAW_BASE_URL}/dev/docker/develop/Dockerfile",
             "${env.GIT_RAW_BASE_URL}/dev/docker/develop/Dockerfile",
             "${env.GIT_RAW_BASE_URL}/dev/docker/develop/Dockerfile",
             scmVars,
             environment,
             ['PARALLELISM': parallelism])
        if (pushDockerTag) {
          utils.dockerPush(iC, "${platform}-develop-build")
          developDockerManifestPush(iC, environment)
        }
        docker.image('postgres:9.5').withRun(""
          + " -e POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
          + " -e POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
          + " --name ${env.IROHA_POSTGRES_HOST}"
          + " --network=${env.IROHA_NETWORK}") {
          iC.inside(""
          + " -e IROHA_POSTGRES_HOST=${env.IROHA_POSTGRES_HOST}"
          + " -e IROHA_POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
          + " -e IROHA_POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
          + " --network=${env.IROHA_NETWORK}"
          + " -v /var/jenkins/ccache:${env.CCACHE_DEBUG_DIR}") {
            utils.ccacheSetup(5)
            build.cmakeConfigure(buildDir, "-DCMAKE_CXX_COMPILER=${compilers[compilerVersion]['cxx_compiler']} \
              -DCMAKE_CC_COMPILER=${compilers[compilerVersion]['cc_compiler']} -DCMAKE_BUILD_TYPE=Debug \
              -DCOVERAGE=${cmakeBooleanOption[coverage]} -DTESTING=${cmakeBooleanOption[testing]}")
            build.cmakeBuild(buildDir, "", parallelism)
            if (testing) {
              coverage ? initialCoverage(buildDir) : sh('echo Skipping initial coverage...')
              testSteps(buildDir, environment)
              coverage ? postCoverage(buildDir) : sh('echo Skipping post coverage...')
            }
            cppcheck ? build.cppCheck(buildDir, parallelism) : sh('echo Skipping Cppcheck...')
            sonar ? build.sonarScanner(scmVars, environment) : sh('echo Skipping Sonar Scanner...')
          }
        }
      }
    }
  }
}

def alwaysPostSteps(List environment) {
  withEnv(environment) {
    sh "docker network rm $IROHA_NETWORK || true"
    cleanWs()
  }
}

return this