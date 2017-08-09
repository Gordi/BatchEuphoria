/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs

import de.dkfz.roddy.config.ResourceSet
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import java.util.concurrent.atomic.AtomicLong

/**
 * The job class represents a generic and abstract form of cluster job which can be run using a job manager.
 * When a job is executed with the JM, the used Command and a BEJobResult object will be created and added to this
 * object.
 */
@groovy.transform.CompileStatic
class BEJob<J extends BEJob, JR extends BEJobResult> {

    private static final de.dkfz.roddy.tools.LoggerWrapper logger = de.dkfz.roddy.tools.LoggerWrapper.getLogger(BEJob.class.getSimpleName())

    protected JobType jobType = JobType.STANDARD

    private final List<BEJob> arrayChildJobs = new LinkedList<>()

    private static AtomicLong absoluteJobCreationCounter = new AtomicLong()

    /**
     * The name of the job which should be passed to the execution system.
     */
    public final String jobName

    public boolean isDirty

    /**
     * An internal job creation count. Has nothing to do with e.g. PBS / cluster / process id's!
     */
    public final long jobCreationCounter = absoluteJobCreationCounter.incrementAndGet()

    /**
     * The tool you want to call.
     */
    protected File tool

    /**
     * MD5 sum of the called tool
     */
    protected String toolMD5

    /**
     * A tool script which will be piped (or whatever...) to the job manager submission command / method
     * It is either testScript OR tool
     */
    String toolScript
    /**
     * The set of resources for this tool / BEJob
     * Contains values for e.g. maxMemory, walltime and so on.
     */
    protected ResourceSet resourceSet

    /**
     * Parameters for the tool you want to call
     */
    protected final Map<String, String> parameters

    /**
     * If you want to generated arrays use this. <p>You can do things like: n-m,
     */
    protected final List<String> arrayIndices

    protected final List<J> parentJobs

    /**
     * You should provide i.e. job ids of qsub jobs to automatically create job
     * dependencies.
     */
    protected final List<BEJobID> listOfCustomDependencyIDs = new LinkedList<>()

    /**
     * Temporary value which defines the jobs jobState.
     */
    protected transient JobState currentJobState

    private List<ProcessingCommands> processingCommand = new LinkedList<ProcessingCommands>()

    /**
     * Command object of last execution.
     */
    protected transient Command lastCommand

    /**
     * Stores the result when the job was executed.
     */
    JR runResult

    //////////////////////////////////////////////////////////////
    // Now come some job / command specific settings
    //////////////////////////////////////////////////////////////

    /**
     * The custom job log directory. If it is not set, the job manager default will be used (if supported)
     */
    protected File loggingDirectory

    /**
     * Set this, to have use a custom account for the job. (If supported by the target job manager)
     */
    String customUserAccount

    /**
     * Set this to use a custom queue for the job. (If supported by the target job manager)
     */
    String customQueue

    /**
     * Stores information from the cluster about the job e.g. used resources
     */
    GenericJobInfo jobInfo

    BatchEuphoriaJobManager jobManager


    private static List<BEJobID> jobs2jobIDs(List<BEJob> jobs) {
        if (null == jobs) {
            return new LinkedList<BEJobID>()
        } else {
            return jobs.collect { it.runResult.jobID }
        }
    }

    private List<BEJobID> reconcileParentJobInformation(List<BEJobID> parentJobIDs, List<BEJob> parentJobs) {
        List<BEJobID> pJids
        if ((null != parentJobIDs && !parentJobIDs.isEmpty()) &&
                (null != parentJobs && !parentJobs.isEmpty())) {
            def a = findValidJobIDs(parentJobIDs).collect{ it.toString() }
            def b = jobs2jobIDs(findJobsWithValidJobId(parentJobs)).collect { it.toString() }
            if (a != b) {
                throw new RuntimeException("parentJobBEJob needs to be called with one of parentJobs, parentJobIDs, or parentJobsIDs and *corresponding* parentJobs.")
            }
            pJids = findValidJobIDs(parentJobIDs)
        } else if (null == parentJobIDs && null == parentJobs) {
            pJids = new LinkedList<BEJobID>()
        } else if (null != parentJobs) {
            pJids = jobs2jobIDs(findJobsWithValidJobId(parentJobs))
        } else {
            pJids == findValidJobIDs(parentJobIDs)
        }
        return pJids;
    }

    BEJob(String jobName, File tool, String toolScript, String toolMD5, ResourceSet resourceSet, List<String> arrayIndices, Map<String, String> parameters, List<BEJob> parentJobs, List<BEJobID> parentJobIDs, BatchEuphoriaJobManager jobManager) {
        this.jobName = jobName
        this.currentJobState = JobState.UNSTARTED
        this.tool = tool
        this.toolScript = toolScript
        if (tool && toolScript) throw new RuntimeException("A job must only have an input script or a callable file.")
        this.toolMD5 = toolMD5
        this.resourceSet = resourceSet
        this.parameters = parameters
        this.parentJobs = parentJobs
        this.arrayIndices = arrayIndices ?: new LinkedList<String>()
        this.listOfCustomDependencyIDs.addAll(reconcileParentJobInformation(parentJobIDs, parentJobs))
        this.jobManager = jobManager
    }


    protected void setJobType(JobType jobType) {
        this.jobType = jobType
    }

    //TODO Create a runArray method which returns several job results with proper array ids.
    JR run() {

    }

    boolean isFakeJob() {
        if (this instanceof FakeBEJob)
            return true
        if (jobName != null && jobName.equals("Fakejob"))
            return true
        String jobID = getJobID()
        if (jobID == null)
            return false
        return BEFakeJobID.isFakeJobID(jobID)
    }

    protected void postProcessArrayJob(JR runResult) {
        throw new NotImplementedException()
        Map<String, Object> prmsAsStringMap = new LinkedHashMap<>()
        for (String k : parameters.keySet()) {
            prmsAsStringMap.put(k, parameters.get(k))
        }
        jobType = JobType.ARRAY_HEAD
        //TODO Think of proper array index handling!
        int i = 1
    }

    void addProcessingCommand(ProcessingCommands processingCommand) {
        if (processingCommand == null) return
        this.processingCommand.add(processingCommand)
    }

    JobType getJobType() {
        return jobType
    }

    Map<String, String> getParameters() {
        return parameters
    }

    List<String> finalParameters() {
        return parameters.collect { String k, String v -> return "${k}=${v}".toString() }
    }

    List<ProcessingCommands> getListOfProcessingCommand() {
        return [jobManager.convertResourceSet(this)] + processingCommand
    }

    List<J> getParentJobs() {
        return parentJobs
    }

    static List<BEJob> findJobsWithValidJobId(List<BEJob> jobs) {
        return jobs.findAll { !it.isFakeJob() }.sort { it.getJobID().toString() }.unique { it.getJobID() }
    }

    static List<BEJobID> findValidJobIDs(List<BEJobID> jobIDs) {
        return jobIDs.findAll { it.isValidID() }.sort { it.toString() }.unique()
    }

    List<BEJobID> getDependencyIDs() {
        if (listOfCustomDependencyIDs) {
            return listOfCustomDependencyIDs
        } else {
            def parentJobs = getParentJobs()
            if (null == parentJobs) {
                return new LinkedList<BEJobID>()
            } else {
                return findJobsWithValidJobId(parentJobs)?.collect { it.runResult.jobID }
            }
        }
    }

    List<String> getDependencyIDsAsString() {
        return getDependencyIDs().collect { BEJobID jid -> jid.toString() }
    }

    void setLoggingDirectory(File loggingDirectory) {
        this.loggingDirectory = loggingDirectory
    }

    File getLoggingDirectory() {
        if(this.loggingDirectory)
            return this.loggingDirectory
        else
            return jobManager.getLoggingDirectoryForJob(this)
    }

    /**
     * If the job was executed this return the jobs id otherwise null.
     *
     * @return
     */
    String getJobID() {
        if (runResult != null)
            if (runResult.getJobID() != null)
                return runResult.getJobID().getShortID()
            else
                return "Unknown"
        else
            return null
    }

//    String getToolID() {
//        return toolID
//    }

    File getTool() {
        return tool
    }

    String getToolScript() {
        return toolScript
    }

    String getToolMD5() {
        return toolMD5
    }

    ResourceSet getResourceSet() {
        return resourceSet
    }

    protected File _logFile = null
    /**
     * Returns the path to an existing log file.
     * If no logfile exists this returns null.
     *
     * @return
     */
    public synchronized File getLogFile() {
//        if (_logFile == null)
//            _logFile = this.getExecutionContext().getRuntimeService().getLogFileForJob(this);
//        return _logFile;
    }

    public boolean hasLogFile() {
//        if (getJobState().isPlannedOrRunning())
//            return false;
//        if (_logFile == null)
//            return this.getExecutionContext().getRuntimeService().hasLogFileForJob(this);
//        return true;
        return false
    }

    String getJobName() {
        return jobName
    }

    File getParameterFile() {
        return null
    }

    void setJobState(JobState js) {
        if (jobType == JobType.ARRAY_HEAD)
            return
        JobState old = this.currentJobState
        this.currentJobState = js
    }

    JobState getJobState() {
        return currentJobState != null ? currentJobState : JobState.UNKNOWN
    }

    Command getLastCommand() {
        return lastCommand
    }

    @Override
    String toString() {
        if (getToolScript()) {
            return "BEJob: ${jobName} with piped script:\n\t" + getToolScript().readLines().join("\n\t")
        } else {
            return "BEJob: ${jobName} calling tool ${tool.getAbsolutePath()}"
        }
    }
}
