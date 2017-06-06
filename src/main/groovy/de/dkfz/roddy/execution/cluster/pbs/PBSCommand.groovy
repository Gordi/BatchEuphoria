/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.cluster.pbs

import de.dkfz.roddy.execution.jobs.Command
import de.dkfz.roddy.execution.jobs.BEJob
import de.dkfz.roddy.execution.jobs.ProcessingCommands
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.tools.LoggerWrapper

import java.util.logging.Level

import static de.dkfz.roddy.StringConstants.*

/**
 * This class is used to create and execute qsub commands
 *
 * @author michael
 */
@groovy.transform.CompileStatic
class PBSCommand extends Command {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(PBSCommand.class.name)

    public static final String NONE = "none"
    public static final String NONE_ARR = "none[]"
    public static final String AFTEROK = "afterok"
    public static final String AFTEROK_ARR = "afterokarray"
    public static final String QSUB = "qsub"
    public static final String PARM_ACCOUNT = " -A "
    public static final String PARM_JOBNAME = " -N "
    public static final String PARM_JOINLOGS = " -j oe"
    public static final String PARM_LOGPATH = " -o "
    public static final String PARM_MAIL = " -M "
    public static final String PARM_GROUPLIST = " -W group_list="
    public static final String PARM_UMASK = " -W umask="
    public static final String PARM_DEPENDS = " -W depend="
    public static final String PARM_VARIABLES = " -v "
    public static final String PARM_WRAPPED_SCRIPT = "WRAPPED_SCRIPT="

    /**
     * The qsub log directoy where all output is put
     */
    protected File loggingDirectory

    /**
     * The command which should be called
     */
    protected String command

    /**
     * Provide a lower and upper array index to make this qsub job an array job
     */
    protected List<String> arrayIndices

    protected List<String> dependencyIDs

    protected final List<ProcessingCommands> processingCommands

    /**
     *
     * @param id
     * @param parameters
     * @param arrayIndices
     * @param command
     * @param filesToCheck
     */
    PBSCommand(PBSJobManager parentManager, BEJob job, String id, List<ProcessingCommands> processingCommands, Map<String, String> parameters, Map<String, Object> tags, List<String> arrayIndices, List<String> dependencyIDs, String command, File loggingDirectory) {
        super(parentManager, job, id, parameters, tags)
        this.processingCommands = processingCommands
        this.command = command
        this.loggingDirectory = loggingDirectory
        this.arrayIndices = arrayIndices ?: new LinkedList<String>()
        this.dependencyIDs = dependencyIDs ?: new LinkedList<String>()
    }

    boolean getIsArray() {
        return arrayIndices != null && arrayIndices.size() > 0
    }

    String getJoinLogParameter() {
        return PARM_JOINLOGS
    }

    String getEmailParameter(String address) {
        return PARM_MAIL + address
    }

    String getGroupListString(String groupList) {
        return PARM_GROUPLIST + groupList
    }

    String getUmaskString(String umask) {
        return PARM_UMASK + umask
    }

    String getDependencyParameterName() {
        return AFTEROK
    }

    String getVariablesParameter() {
        return PARM_VARIABLES
    }

    /**
     * In this case i.e. afterokarray:...,afterok:
     * A comma
     * @return
     */
    protected String getDependencyTypesSeparator() {

    }

    protected String getDependencyOptionSeparator() {
        return COLON
    }

    protected String getDependencyIDSeparator() {
        return COLON
    }

    protected String getArrayDependencyParameterName() {
        return AFTEROK_ARR
    }

    protected String getAdditionalCommandParameters() {
        return ""
    }

    protected String getDependsSuperParameter() {
        PARM_DEPENDS
    }

    @Override
    String toString() {

        String email = parentJobManager.getUserEmail()
        String umask = parentJobManager.getUserMask()
        String groupList = parentJobManager.getUserGroup()
        String accountName = parentJobManager.getUserAccount()
        boolean useParameterFile = parentJobManager.isParameterFileEnabled()
        boolean holdJobsOnStart = parentJobManager.isHoldJobsEnabled()

        StringBuilder qsubCall = new StringBuilder(EMPTY)

        if (job.getToolScript()) {
            qsubCall << "echo '" << job.getToolScript() << "' | "
        }

        qsubCall << QSUB << PARM_JOBNAME << id

        if (holdJobsOnStart) qsubCall << " -h "

        if (accountName) qsubCall << PARM_ACCOUNT << accountName << " "

        qsubCall << getAdditionalCommandParameters()

        if (loggingDirectory) qsubCall << PARM_LOGPATH << loggingDirectory

        qsubCall << getJoinLogParameter()

        if (isArray) qsubCall << assembleArraySettings()

        if (email) qsubCall << getEmailParameter(email)

        if (groupList && groupList != "UNDEFINED") qsubCall << getGroupListString(groupList)

        if (umask) qsubCall << getUmaskString(umask)

        qsubCall << assembleProcessingCommands()

        qsubCall << assembleDependencyString()

        qsubCall << assembleVariableExportString()

        if (job.getTool()) {
            qsubCall << " " << job.getTool().getAbsolutePath()
        }

        return qsubCall
    }

    StringBuilder assembleArraySettings() {
        StringBuilder qsubCall = new StringBuilder(" -t ")
        StringBuilder sbArrayIndices = new StringBuilder("")
        //TODO Make a second list of array indices, which is valid for job submission. The current translation with the help of counting is not optimal!
        int i = 1 //TODO Think if pbs arrays should always start with one?
        for (String ai in arrayIndices) {
            if (ai.isNumber())
                sbArrayIndices << ai.toInteger()
            else
                sbArrayIndices << i
            sbArrayIndices << StringConstants.COMMA
            i++
        }
        qsubCall << sbArrayIndices.toString()[0..-2]
        return qsubCall
    }

    StringBuilder assembleProcessingCommands() {
        StringBuilder qsubCall = new StringBuilder()
        for (ProcessingCommands pcmd in job.getListOfProcessingCommand()) {
            if (!(pcmd instanceof PBSResourceProcessingCommand)) continue
            PBSResourceProcessingCommand command = (PBSResourceProcessingCommand) pcmd
            if (command == null)
                continue
            qsubCall << StringConstants.WHITESPACE << command.getProcessingString()
        }
        return qsubCall
    }

    String assembleVariableExportString() {
        StringBuilder qsubCall = new StringBuilder()
        //TODO Properly support parameter files.
//        qsubCall << getVariablesParameter() + PARM_WRAPPED_SCRIPT << command
        qsubCall << getVariablesParameter() << "PARAMETER_FILE=" << job.getParameterFile()
        return qsubCall
    }

    String assembleDependencyString() {
        StringBuilder qsubCall = new StringBuilder("")
        LinkedList<String> tempDependencies = new LinkedList<String>()
        LinkedList<String> tempDependenciesArrays = new LinkedList<String>()
        if (creatingJob.parentJobs) {
            for (String d in (creatingJob.parentJobs as List<BEJob>).findAll { BEJob job -> !job.isFakeJob() }.collect { BEJob job -> job.jobID }) {
                if (d != "" && d != NONE && d != "-1") {
                    if (d.contains("[].")) {
                        tempDependenciesArrays << d.toString()
                    } else {
                        tempDependencies << d.toString()
                    }
                }
            }
        }
        if (tempDependencies.size() > 0 || tempDependenciesArrays.size() > 0) {
            StringBuilder depStrBld = new StringBuilder()
            try {
                depStrBld << EMPTY //Prevent the string to be null!
                //qsub wants the afterokarray before afterok. Don't swap this
                if (tempDependenciesArrays.size() > 0) {
                    depStrBld << getArrayDependencyParameterName() << getDependencyOptionSeparator()
                    for (String d in tempDependenciesArrays) {
                        depStrBld << (d != NONE && d != NONE_ARR && d != "-1" ? d + getDependencyIDSeparator() : EMPTY)
                    }
                    String tmp = depStrBld.toString()[0..-2]
                    depStrBld = new StringBuilder()
                    depStrBld << tmp
                }
                if (tempDependencies.size() > 0) {
                    if (tempDependenciesArrays.size() > 0) {
                        depStrBld << getDependencyTypesSeparator()
                    }

                    String dependencyType = getDependencyParameterName()
                    for (ProcessingCommands pcmd : job.getListOfProcessingCommand()) {
                        if (!(pcmd instanceof ChangedProcessDependencyProcessingCommand))
                            continue
                        ChangedProcessDependencyProcessingCommand dpc = pcmd as ChangedProcessDependencyProcessingCommand
                        if (dpc == null) continue
                        dependencyType = dpc.getProcessDependency().name()
////                        dependencyType = dpc.dependencyOptions.name();
//                        DependencyGroup group = DependencyGroup.getGroup(dpc.dependencyGroupID);
//                        if(job == group.referenceJob) {
//                            continue;
//                        } else {
//                            tempDependencies << group.referenceJob.getJobResult().getJobID().shortID;
//                        }
                    }

                    depStrBld << dependencyType << getDependencyOptionSeparator()
                    for (String d in tempDependencies) {
                        depStrBld << (d != NONE && d != NONE_ARR && d != "-1" ? d + getDependencyIDSeparator() : EMPTY)
                    }
                    String tmp = depStrBld.toString()[0..-2]
                    depStrBld = new StringBuilder()
                    depStrBld << tmp
                }

                String depStr = depStrBld.toString()
                if (depStr.length() > 1) {
                    qsubCall << getDependsSuperParameter() << depStr
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.toString())
            }
        }
        return qsubCall
    }
}
