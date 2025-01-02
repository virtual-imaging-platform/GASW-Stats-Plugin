/* Copyright CNRS-CREATIS
 *
 * Rafael Ferreira da Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.rafaelsilva.com
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */
package fr.insalyon.creatis.gasw.plugin.listener.stats;

import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswOutput;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.bean.JobMinorStatus;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.plugin.ListenerPlugin;
import fr.insalyon.creatis.gasw.plugin.listener.stats.dao.StatsPluginDAOFactory;
import fr.insalyon.creatis.moteur.plugins.workflowsdb.WorkflowsDBException;
import fr.insalyon.creatis.moteur.plugins.workflowsdb.bean.Stats;
import fr.insalyon.creatis.moteur.plugins.workflowsdb.dao.StatsDAO;
import fr.insalyon.creatis.moteur.plugins.workflowsdb.dao.WorkflowsDBDAOException;
import fr.insalyon.creatis.moteur.plugins.workflowsdb.dao.WorkflowsDBDAOFactory;

import java.util.*;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Ferreira da Silva
 */
@PluginImplementation
public class StatsListener implements ListenerPlugin {

    private static final Logger logger = Logger.getLogger("fr.insalyon.creatis.gasw");
    private StatsDAO statsDAO;
    private WorkflowsDBDAOFactory workflowsDBDAOFactory;

    @Override
    public String getPluginName() {
        return StatsConstants.NAME;
    }

    @Override
    public List<Class> getPersistentClasses() throws GaswException {
        return new ArrayList<Class>();
    }

    @Override
    public void load() throws GaswException {

        try {
            logger.info("Loading Workflow Stats GASW Plugin");
            workflowsDBDAOFactory = new WorkflowsDBDAOFactory();
            statsDAO = workflowsDBDAOFactory.getStatsDAO();

        } catch (WorkflowsDBDAOException | WorkflowsDBException ex) {
            logger.error(ex);
            throw new GaswException(ex);
        }
    }

    @Override
    public void jobSubmitted(Job job) throws GaswException {
    }

    @Override
    public synchronized void jobFinished(GaswOutput gaswOutput) throws GaswException {

        try {
            List<Job> jobs = StatsPluginDAOFactory.getInstance().getJobDAO().getByFilenameAndExitCode(
                    gaswOutput.getJobID().replace(".jdl", ""), gaswOutput.getExitCode());
            Job job = selectLastjob(jobs);
            Stats stats = statsDAO.get(job.getSimulationID());

            boolean exists = true;
            if (stats == null) {
                stats = new Stats(job.getSimulationID());
                exists = false;
            }

            parseStatus(job, stats);

            if (exists) {
                statsDAO.update(stats);
            } else {
                statsDAO.add(stats);
            }

        } catch (DAOException ex) {
            throw new GaswException(ex);
        } catch (WorkflowsDBDAOException ex) {
            logger.error(ex);
            throw new GaswException(ex);
        }
    }

    @Override
    public void jobStatusChanged(Job job) throws GaswException {
    }

    @Override
    public void jobMinorStatusReported(JobMinorStatus jobMinorStatus) throws GaswException {
    }

    @Override
    public void terminate() {
        workflowsDBDAOFactory.close();
    }

    private Job selectLastjob(List<Job> jobs) throws GaswException {
        if (jobs.size() == 1) {
            return jobs.get(0);
        } else if (jobs.stream().anyMatch(j -> j.getEnd() == null)) {
            logger.error("[GASW Stats] Cannot select job for " +
                    jobs.get(0).getSimulationID() + " because one of them " +
                    "does not have an end date");
            throw new GaswException("[GASW Stats] Cannot select job for " +
                    jobs.get(0).getSimulationID() + " because one of them " +
                    "does not have an end date");
        } else {
            return jobs.stream().max(Comparator.comparing(Job::getEnd)).get();
        }
    }

    /**
     * Parses the status of the job and sets its times.
     *
     * @param job Job object
     * @param stats Stats object
     */
    private void parseStatus(Job job, Stats stats) {

        switch (job.getStatus()) {

            case COMPLETED:
                if (job.getExitCode() == 0) {
                    stats.setCompleted(stats.getCompleted() + 1);
                    stats.setCompletedWaitingTime(stats.getCompletedWaitingTime()
                            + (job.getDownload().getTime() - job.getQueued().getTime()) / 1000);
                    stats.setCompletedInputTime(stats.getCompletedInputTime()
                            + (job.getRunning().getTime() - job.getDownload().getTime()) / 1000);
                    stats.setCompletedExecutionTime(stats.getCompletedExecutionTime()
                            + (job.getUpload().getTime() - job.getRunning().getTime()) / 1000);
                    stats.setCompletedOutputTime(stats.getCompletedOutputTime()
                            + (job.getEnd().getTime() - job.getUpload().getTime()) / 1000);
                }
                break;

            case CANCELLED:
            case DELETED:
                stats.setCancelled(stats.getCancelled() + 1);
                if (job.getQueued() != null) {
                    stats.setCancelledWaitingTime(stats.getCancelledWaitingTime()
                            + getTime(job.getQueued(), job.getDownload(), job.getEnd()));
                }
                if (job.getDownload() != null) {
                    stats.setCancelledInputTime(stats.getCancelledInputTime()
                            + getTime(job.getDownload(), job.getRunning(), job.getEnd()));
                }
                if (job.getRunning() != null) {
                    stats.setCancelledExecutionTime(stats.getCancelledExecutionTime()
                            + getTime(job.getRunning(), job.getUpload(), job.getEnd()));
                }
                if (job.getUpload() != null) {
                    stats.setCancelledOutputTime(stats.getCancelledOutputTime()
                            + (job.getEnd().getTime() - job.getUpload().getTime()) / 1000);
                }
                break;

            case STALLED:
                stats.setFailedStalled(stats.getFailedStalled() + 1);
                if (job.getQueued() != null) {
                    stats.setFailedStalledWaitingTime(stats.getFailedStalledWaitingTime()
                            + getTime(job.getQueued(), job.getDownload(), job.getEnd()));
                }
                if (job.getDownload() != null) {
                    stats.setFailedStalledInputTime(stats.getFailedStalledInputTime()
                            + getTime(job.getDownload(), job.getRunning(), job.getEnd()));
                }
                if (job.getRunning() != null) {
                    stats.setFailedStalledExecutionTime(stats.getFailedStalledExecutionTime()
                            + getTime(job.getRunning(), job.getUpload(), job.getEnd()));
                }
                if (job.getUpload() != null) {
                    stats.setFailedStalledOutputTime(stats.getFailedStalledOutputTime()
                            + (job.getEnd().getTime() - job.getUpload().getTime()) / 1000);
                }
                break;

            case ERROR:

                switch (job.getExitCode()) {
                    // Inputs Error
                    case 1:
                        stats.setFailedInput(stats.getFailedInput() + 1);
                        if (job.getQueued() != null) {
                            stats.setFailedInputWaitingTime(stats.getFailedInputWaitingTime()
                                    + getTime(job.getQueued(), job.getDownload(), job.getEnd()));
                        }
                        if (job.getDownload() != null) {
                            stats.setFailedInputInputTime(stats.getFailedInputInputTime()
                                    + getTime(job.getDownload(), job.getRunning(), job.getEnd()));
                        }
                        if (job.getRunning() != null) {
                            stats.setFailedInputExecutionTime(stats.getFailedInputExecutionTime()
                                    + getTime(job.getRunning(), job.getUpload(), job.getEnd()));
                        }
                        if (job.getUpload() != null) {
                            stats.setFailedInputOutputTime(stats.getFailedInputOutputTime()
                                    + (job.getEnd().getTime() - job.getUpload().getTime()) / 1000);
                        }
                        break;

                    // Outputs Error
                    case 2:
                        stats.setFailedOutput(stats.getFailedOutput() + 1);
                        if (job.getQueued() != null) {
                            stats.setFailedOutputWaitingTime(stats.getFailedOutputWaitingTime()
                                    + getTime(job.getQueued(), job.getDownload(), job.getEnd()));
                        }
                        if (job.getDownload() != null) {
                            stats.setFailedOutputInputTime(stats.getFailedOutputInputTime()
                                    + getTime(job.getDownload(), job.getRunning(), job.getEnd()));
                        }
                        if (job.getRunning() != null) {
                            stats.setFailedOutputExecutionTime(stats.getFailedOutputExecutionTime()
                                    + getTime(job.getRunning(), job.getUpload(), job.getEnd()));
                        }
                        if (job.getUpload() != null) {
                            stats.setFailedOutputOutputTime(stats.getFailedOutputOutputTime()
                                    + (job.getEnd().getTime() - job.getUpload().getTime()) / 1000);
                        }
                        break;

                    // Application Error
                    case 6:
                        stats.setFailedApplication(stats.getFailedApplication() + 1);
                        if (job.getQueued() != null) {
                            stats.setFailedApplicationWaitingTime(stats.getFailedApplicationWaitingTime()
                                    + getTime(job.getQueued(), job.getDownload(), job.getEnd()));
                        }
                        if (job.getDownload() != null) {
                            stats.setFailedApplicationInputTime(stats.getFailedApplicationInputTime()
                                    + getTime(job.getDownload(), job.getRunning(), job.getEnd()));
                        }
                        if (job.getRunning() != null) {
                            stats.setFailedApplicationExecutionTime(stats.getFailedApplicationExecutionTime()
                                    + getTime(job.getRunning(), job.getUpload(), job.getEnd()));
                        }
                        if (job.getUpload() != null) {
                            stats.setFailedApplicationOutputTime(stats.getFailedApplicationOutputTime()
                                    + (job.getEnd().getTime() - job.getUpload().getTime()) / 1000);
                        }
                }
        }
    }

    /**
     *
     * @param endTime
     * @param finalTime
     * @return
     */
    private long getTime(Date initialTime, Date endTime, Date finalTime) {

        return ((endTime != null ? endTime.getTime() : finalTime.getTime())
                - initialTime.getTime()) / 1000;
    }
}
