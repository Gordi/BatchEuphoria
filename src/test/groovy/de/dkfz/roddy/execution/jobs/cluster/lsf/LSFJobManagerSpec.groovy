/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs.cluster.lsf

import de.dkfz.roddy.TestExecutionService
import de.dkfz.roddy.execution.BEExecutionService
import de.dkfz.roddy.execution.io.ExecutionResult
import de.dkfz.roddy.execution.jobs.BEJobID
import de.dkfz.roddy.execution.jobs.GenericJobInfo
import de.dkfz.roddy.execution.jobs.JobManagerOptions
import de.dkfz.roddy.execution.jobs.JobState
import de.dkfz.roddy.tools.BufferUnit
import de.dkfz.roddy.tools.BufferValue
import groovy.json.JsonSlurper
import spock.lang.Specification

import java.lang.reflect.Method
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class LSFJobManagerSpec extends Specification {

    final static String RAW_JSON_OUTPUT_WITHOUT_LISTS = '''
{
  "COMMAND":"bjobs",
  "JOBS":1,
  "RECORDS":[
    {
      "JOBID":"22005",
      "JOB_NAME":"ls -l",
      "STAT":"DONE",
      "USER":"otptest",
      "QUEUE":"short-dmg",
      "JOB_DESCRIPTION":"",
      "PROJ_NAME":"default",
      "JOB_GROUP":"",
      "JOB_PRIORITY":"",
      "PIDS":"51904",
      "EXIT_CODE":"1",
      "FROM_HOST":"tbi-cn013",
      "EXEC_HOST":"tbi-cn020",
      "SUBMIT_TIME":"Dec 28 19:56",
      "START_TIME":"Dec 28 19:56",
      "FINISH_TIME":"Dec 28 19:56 L",
      "CPU_USED":"00:00:01",
      "RUN_TIME":"00:00:01",
      "USER_GROUP":"",
      "SWAP":"0 Mbytes",
      "MAX_MEM":"522 MBytes",
      "RUNTIMELIMIT":"00:10:00",
      "SUB_CWD":"$HOME",
      "PEND_REASON":"Job dependency condition not satisfied;",
      "EXEC_CWD":"\\/home\\/otptest",
      "OUTPUT_FILE":"\\/sequencing\\/whole_genome_sequencing\\/coveragePlotSingle.o30060",
      "INPUT_FILE":"",
      "EFFECTIVE_RESREQ":"select[type == local] order[r15s:pg] ",
      "EXEC_HOME":"\\/home\\/otptest",
      "SLOTS":"1",
      "ERROR_FILE":"",
      "COMMAND":"ls -l",
      "DEPENDENCY":"done(22004)"
    }
  ]
}
'''

    void "queryJobInfo, bjobs JSON output with lists  "() {

        given:
        def parms = JobManagerOptions.create().build()
        TestExecutionService testExecutionService = new TestExecutionService("test", "test")
        LSFJobManager jm = new LSFJobManager(testExecutionService, parms)
        Method method = LSFJobManager.class.getDeclaredMethod("queryJobInfo", Map)
        method.setAccessible(true)
        Object parsedJson = new JsonSlurper().parseText(RAW_JSON_OUTPUT)
        List records = (List) parsedJson.getAt("RECORDS")

        when:
        GenericJobInfo jobInfo = method.invoke(jm, records.get(0))

        then:
        jobInfo != null
        jobInfo.tool == new File("ls -l")
    }

    void "queryJobInfo, bjobs JSON output without lists  "() {

        given:
        def parms = JobManagerOptions.create().build()
        TestExecutionService testExecutionService = new TestExecutionService("test", "test")
        LSFJobManager jm = new LSFJobManager(testExecutionService, parms)
        Method method = LSFJobManager.class.getDeclaredMethod("queryJobInfo", Map)
        method.setAccessible(true)
        Object parsedJson = new JsonSlurper().parseText(RAW_JSON_OUTPUT_WITHOUT_LISTS)
        List records = (List) parsedJson.getAt("RECORDS")

        when:
        GenericJobInfo jobInfo = method.invoke(jm, records.get(0))

        then:
        jobInfo != null
        jobInfo.tool == new File("ls -l")
    }

    void "queryJobInfo, bjobs JSON output empty  "() {

        given:
        String emptyRawJsonOutput = '''
        {
            "COMMAND":"bjobs",
            "JOBS":1,
            "RECORDS":[
                {
                "JOBID":"22005",
                }
            ]
        }
        '''
        def parms = JobManagerOptions.create().build()
        TestExecutionService testExecutionService = new TestExecutionService("test", "test")
        LSFJobManager jm = new LSFJobManager(testExecutionService, parms)
        Method method = LSFJobManager.class.getDeclaredMethod("queryJobInfo", Map)
        method.setAccessible(true)
        Object parsedJson = new JsonSlurper().parseText(emptyRawJsonOutput)
        List records = (List) parsedJson.getAt("RECORDS")

        when:
        GenericJobInfo jobInfo = method.invoke(jm, records.get(0))

        then:
        jobInfo.jobID.toString() == "22005"
    }

    void "test queryExtendedJobStateById"() {
        given:
        JobManagerOptions parms = JobManagerOptions.create().build()
        def jsonFile = getResourceFile("de/dkfz/roddy/execution/jobs/cluster/lsf/queryExtendedJobStateByIdTest.json")
        BEExecutionService testExecutionService = [
                execute: { String s -> new ExecutionResult(true, 0, jsonFile.readLines(), null) }
        ] as BEExecutionService
        LSFJobManager manager = new LSFJobManager(testExecutionService, parms)

        when:
        Map<BEJobID, GenericJobInfo> result = manager.queryExtendedJobStateById([new BEJobID("22005")])

        then:
        result.size() == 1
        GenericJobInfo jobInfo = result.get(new BEJobID("22005"))
        jobInfo
        jobInfo.askedResources.size == null
        jobInfo.askedResources.mem == null
        jobInfo.askedResources.cores == null
        jobInfo.askedResources.nodes == null
        jobInfo.askedResources.walltime == Duration.ofMinutes(10)
        jobInfo.askedResources.storage == null
        jobInfo.askedResources.queue == "short-dmg"
        jobInfo.askedResources.nthreads == null
        jobInfo.askedResources.swap == null

        jobInfo.usedResources.size == null
        jobInfo.usedResources.mem == new BufferValue(5452595, BufferUnit.k)
        jobInfo.usedResources.cores == null
        jobInfo.usedResources.nodes == 1
        jobInfo.usedResources.walltime == Duration.ofSeconds(1)
        jobInfo.usedResources.storage == null
        jobInfo.usedResources.queue == "short-dmg"
        jobInfo.usedResources.nthreads == null
        jobInfo.usedResources.swap == null

        jobInfo.jobName == "ls -l"
        jobInfo.tool == new File("ls -l")
        jobInfo.jobID == new BEJobID("22005")
        jobInfo.submitTime == ZonedDateTime.of(2017, 12, 28, 19, 56, 0, 0, ZoneId.systemDefault())
        jobInfo.eligibleTime == null
        jobInfo.startTime == ZonedDateTime.of(2017, 12, 28, 19, 56, 0, 0, ZoneId.systemDefault())
        jobInfo.endTime == ZonedDateTime.of(2017, 12, 28, 19, 56, 0, 0, ZoneId.systemDefault())
        jobInfo.executionHosts == ["tbi-cn019", "tbi-cn019"]
        jobInfo.submissionHost == "tbi-cn013"
        jobInfo.priority == null
        jobInfo.logFile == null
        jobInfo.errorLogFile == null
        jobInfo.inputFile == null
        jobInfo.user == "otptest"
        jobInfo.userGroup == null
        jobInfo.resourceReq == 'select[type == local] order[r15s:pg] '
        jobInfo.startCount == null
        jobInfo.account == null
        jobInfo.server == null
        jobInfo.umask == null
        jobInfo.parameters == null
        jobInfo.parentJobIDs == ["22004"]
        jobInfo.otherSettings == null
        jobInfo.jobState == JobState.COMPLETED_SUCCESSFUL
        jobInfo.userTime == null
        jobInfo.systemTime == null
        jobInfo.pendReason == null
        jobInfo.execHome == "/home/otptest"
        jobInfo.execUserName == null
        jobInfo.pidStr == ["46782", "46796", "46798", "46915", "47458", "47643"]
        jobInfo.pgidStr == null
        jobInfo.exitCode == 0
        jobInfo.jobGroup == null
        jobInfo.description == null
        jobInfo.execCwd == "/home/otptest"
        jobInfo.askedHostsStr == null
        jobInfo.cwd == '$HOME'
        jobInfo.projectName == "default"
        jobInfo.cpuTime == Duration.ofSeconds(1)
        jobInfo.runTime == Duration.ofSeconds(1)
        jobInfo.timeUserSuspState == null
        jobInfo.timePendState == null
        jobInfo.timePendSuspState == null
        jobInfo.timeSystemSuspState == null
        jobInfo.timeUnknownState == null
        jobInfo.timeOfCalculation == null
    }

    static final File getResourceFile(String file) {
        new File("src/test/resources", file)
    }

    def "test convertBJobsResultLinesToResultMap"() {
        given:
        def jsonFile = getResourceFile("de/dkfz/roddy/execution/jobs/cluster/lsf/convertBJobsResultLinesToResultMapTest.json")
        def json = jsonFile.text

        when:
        Map<BEJobID, Map<String, Object>> map = LSFJobManager.convertBJobsJsonOutputToResultMap(json)
        def jobId = map.keySet()[0]

        then:
        map.size() == 6
        jobId.id == "487641"
        map[jobId]["JOBID"] == "487641"
        map[jobId]["JOB_NAME"] == "RoddyTest_testScript"
        map[jobId]["STAT"] == "EXIT"
        map[jobId]["FINISH_TIME"] == "Jan  7 09:59 L"
    }

    def "test filterJobMapByAge"() {
        given:
        def jsonFile = getResourceFile("de/dkfz/roddy/execution/jobs/cluster/lsf/convertBJobsResultLinesToResultMapTest.json")
        def json = jsonFile.text
        LocalDateTime referenceTime = LocalDateTime.of(2019, 01, 8, 14, 29, 2, 25)

        when:
        def records = LSFJobManager.convertBJobsJsonOutputToResultMap(json)
        records = LSFJobManager.filterJobMapByAge(records, referenceTime, Duration.ofMinutes(10))
        def id = records.keySet()[0]

        then:
        records.size() == 2
        id.id == "491864"
        records[id]["FINISH_TIME"] == "Jan  8 14:20 L"
    }

    def testMassiveConvertBJobsResultLinesToResultMap(def _entries, def value) {
        when:
        int entries = _entries[0]
        String template1 = getResourceFile("bjobsJobTemplatePart1.txt").text
        String template2 = getResourceFile("bjobsJobTemplatePart2.txt").text
        List<String> lines = new LinkedList<>()

        lines << "{"
        lines << '  "COMMAND":"bjobs",'
        lines << '  "JOBS":"' + entries + '",'
        lines << '  "RECORDS":['

        int maximum = 1000000 + entries - 1
        for (int i = 1000000; i <= maximum; i++) {
            lines += template1.readLines()
            lines << '      "JOBID":"' + i + '",'
            lines << '      "JOB_NAME":"r181217_003553288_Strand_T_150_aTestJob",'
            lines += template2.readLines()
            if (i < maximum)
                lines << "      ,"
        }
        lines << "  ]"
        lines << "}"
        println("Entries ${entries}")
        def result = LSFJobManager.convertBJobsJsonOutputToResultMap(lines.join("\n"))

        then:
        result.size() == entries

        where:
        _entries | value
        [1]      | true
        [10]     | true
        [100]    | true
        [1000]   | true
        [2000]   | true
        [4000]   | true
        [8000]   | true
        [16000]  | true
    }
}