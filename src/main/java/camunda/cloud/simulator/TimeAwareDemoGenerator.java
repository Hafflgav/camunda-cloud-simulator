package camunda.cloud.simulator;

import camunda.cloud.clock.ClockActuatorClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class TimeAwareDemoGenerator {
    public static final int METRIC_INTERVAL_MINUTES = 15;
    private static final Logger LOG = LoggerFactory.getLogger(TimeAwareDemoGenerator.class);

    private static TimeAwareDemoGenerator runningInstance;

    private String bpmnProcessId;
    private int numberOfDaysInPast;
    private int numberOfDaysToSkip;
    private StatisticalDistribution timeBetweenStartsBusinessDays;
    private String startTimeBusinessDay;
    private String endTimeBusinessDay;
    private boolean runAlways;
    private boolean includeWeekend = false;
    private ClockActuatorClient clockActuatorClient;
    private String deploymentId;
    private Date previousStartTime;
    private ZeebeClient zeebeClient;
    private BpmnModelInstance originalModelInstance;
    private Date nextMetricTime = null;
    private Date stopTime;
    private Date firstStartTime;
    private Date nextStartTime;
    private DemoModelInstrumentator instrumentator;
    private Date cachedDayStartTime = null;
    private Date cachedDayEndTime = null;
    private BpmnModelInstance tweakedBpmnModelInstance;

    public Date getStopTime() {
        return stopTime;
    }

    public Date getFirstStartTime() {
        return firstStartTime;
    }

    public Date getPreviousStartTime() {
        return previousStartTime;
    }

    public Date getNextStartTime() {
        return nextStartTime;
    }

    private Optional<Double> parseTime(String time) {
        return null;
    }

    public TimeAwareDemoGenerator(ZeebeClient zeebeClient, BpmnModelInstance modelInstance, ClockActuatorClient client) {
        this.zeebeClient = zeebeClient;
        this.originalModelInstance = modelInstance;
        this.clockActuatorClient = client;
    }

    public TimeAwareDemoGenerator timeBetweenStartsBusinessDays(double mean, double standardDeviation) {
        timeBetweenStartsBusinessDays = new StatisticalDistribution(mean, standardDeviation);
        return this;
    }

    public TimeAwareDemoGenerator timeBetweenStartsBusinessDays(String mean, String standardDeviation) {
        timeBetweenStartsBusinessDays = new StatisticalDistribution(Double.parseDouble(mean), Double.parseDouble(standardDeviation));
        return this;
    }

    public TimeAwareDemoGenerator processID(String bpmnProcessId) {
        this.bpmnProcessId = bpmnProcessId;
        return this;
    }

    public TimeAwareDemoGenerator numberOfDaysInPast(int numberOfDaysInPast) {
        this.numberOfDaysInPast = numberOfDaysInPast;
        return this;
    }

    public TimeAwareDemoGenerator skipLastDays(int numberOfDaysToSkip) {
        this.numberOfDaysToSkip = numberOfDaysToSkip;
        return this;
    }

    public TimeAwareDemoGenerator startTimeBusinessDay(String startTimeBusinessDay) {
        this.startTimeBusinessDay = startTimeBusinessDay;
        return this;
    }

    public TimeAwareDemoGenerator endTimeBusinessDay(String endTimeBusinessDay) {
        this.endTimeBusinessDay = endTimeBusinessDay;
        return this;
    }

    public TimeAwareDemoGenerator includeWeekend(boolean includeWeekend) {
        this.includeWeekend = includeWeekend;
        return this;
    }

    public TimeAwareDemoGenerator runAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public long run() {
        if (runningInstance != null) {throw new RuntimeException("There can only be one running instance! (TimeAwareDemoGenerator)");}
        runningInstance = this;

        try {
            instrumentator = new DemoModelInstrumentator();
            tweakedBpmnModelInstance = instrumentator.tweakProcessDefinition(originalModelInstance);

            zeebeClient.newDeployCommand()
                    .addProcessModel(tweakedBpmnModelInstance, "test.bpmn")
                    .send().join();
            try {
                long result = simulate();
                return result;
            } finally {
                zeebeClient.newDeployCommand()
                        .addProcessModel(instrumentator.getOriginalModels(), "test.bpmn")
                        .send().join();
            }
        } finally {
            runningInstance = null;
            return 0;
        }
    }

    protected long simulate() {
        // Removed for now: https://github.com/camunda-consulting/camunda-util-demo-data-generator/tree/master/camunda-util-demo-data-generator/src/main/java/com/camunda/demo/environment/simulation

        // Calculate start date ('numberOfDaysToSkip' into the past), with stopDate = now
        if (stopTime == null) {
            stopTime = new Date();
        }

        Calendar lastTimeToStart = Calendar.getInstance();
        lastTimeToStart.setTime(stopTime);
        lastTimeToStart.add(Calendar.DAY_OF_YEAR, -1 * numberOfDaysToSkip);
        lastTimeToStart.set(Calendar.HOUR_OF_DAY, 0);
        lastTimeToStart.set(Calendar.MINUTE, 0);
        lastTimeToStart.set(Calendar.SECOND, 0);
        lastTimeToStart.set(Calendar.MILLISECOND, 0);

        long startedInstances = 0;
        Set<String> runningProcessInstanceIds = new TreeSet<>();
        Set<String> processInstanceIdsAlreadyReachedCurrentTime = new HashSet<>();

        // calculate time to start first process instance
        nextStartTime = calculateNextStartTime(null, lastTimeToStart.getTime());

        while (true) {
            Optional<Work<?>> workCandidate = calculateNextSimulationStep(stopTime, runningProcessInstanceIds, processInstanceIdsAlreadyReachedCurrentTime);

            if (!workCandidate.isPresent() && nextStartTime == null) {
                break;
            }
            if (nextStartTime != null && (!workCandidate.isPresent() || workCandidate.get().getDue().after(nextStartTime))) {
                if (firstStartTime == null) {
                    firstStartTime = nextStartTime;
                }
                previousStartTime = nextStartTime;

                try {
                    clockActuatorClient.pinZeebeTime(nextStartTime.toInstant());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                //Todo: Add parameters
                ProcessInstanceEvent instance = zeebeClient.newCreateInstanceCommand().processDefinitionKey(0).variables("to be set").send().join();

                startedInstances++;
                runningProcessInstanceIds.add(instance.getBpmnProcessId());
                nextStartTime = calculateNextStartTime(nextStartTime, lastTimeToStart.getTime());

                continue;
            }

            /**
             * Todo: Set time to due date of the next activity
             *
             *
             * if (candidate.get().getDue().after(client.getCurrentTime())) {
             *     ClockUtil.setCurrentTime(candidate.get().getDue());
             * }
             * candidate.get().execute(engine);
             */
        }
        return startedInstances;
    }

    private Date calculateNextStartTime(Date previousStartTime, Date latestStartTime) {
        Calendar nextStartTime = Calendar.getInstance();
        if (previousStartTime == null) {
            nextStartTime = Calendar.getInstance();
            nextStartTime.add(Calendar.DAY_OF_YEAR, -1 * numberOfDaysInPast);
            nextStartTime.set(Calendar.HOUR_OF_DAY, 0);
            nextStartTime.set(Calendar.MINUTE, 0);
            nextStartTime.set(Calendar.SECOND, 0);
            nextStartTime.set(Calendar.MILLISECOND, 0);
        } else {
            nextStartTime.setTime(previousStartTime);
        }

        while (!nextStartTime.getTime().after(latestStartTime)) {
            if (includeWeekend || (nextStartTime.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && nextStartTime.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY)) {
                if (isInTimeFrame(nextStartTime, startTimeBusinessDay, endTimeBusinessDay)) {
                    if (previousStartTime == null || nextStartTime.getTime().after(previousStartTime)) {
                        return nextStartTime.getTime();
                    }
                }
            }
            double time = timeBetweenStartsBusinessDays.nextSample();
            nextStartTime.add(Calendar.SECOND, (int) Math.round(time));
        }
        return null;
    }

    private boolean isInTimeFrame(Calendar cal, String startTime, String endTime) {
        try {
            if (cachedDayStartTime == null || cachedDayEndTime == null) {
                cachedDayStartTime = new SimpleDateFormat("HH:mm").parse(startTime);
                cachedDayEndTime = new SimpleDateFormat("HH:mm").parse(endTime);
            }
            Calendar startCal = Calendar.getInstance();
            startCal.setTime(cachedDayStartTime);
            copyTimeField(cal, startCal, Calendar.YEAR, Calendar.DAY_OF_YEAR);

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(cachedDayEndTime);
            copyTimeField(cal, endCal, Calendar.YEAR, Calendar.DAY_OF_YEAR);

            return (!cal.before(startCal) && cal.before(endCal));
        } catch (ParseException ex) {
            throw new RuntimeException("Could not parse time format: '" + startTime + "' or '" + endTime + "'", ex);
        }
    }

    protected void copyTimeField(Calendar calFrom, Calendar calTo, int... calendarFieldConstant) {
        for (int i = 0; i < calendarFieldConstant.length; i++) {
            calTo.set(calendarFieldConstant[i], calFrom.get(calendarFieldConstant[i]));
        }
    }

    protected Optional<Work<?>> calculateNextSimulationStep(Date theRealNow, Set<String> runningProcessInstanceIds, Set<String> processInstancIdsAlreadyReachedCurrentTime) {
        // select next work to execute (based on due date)
        SortedSet<Work<?>> workList = createWorkList();
        if (workList.size()==0) {
            return Optional.empty();
        } else {
            return Optional.of(workList.first());
        }
    }

    private List<ActivatedJob> jobsForWork;

    private SortedSet<Work<?>> createWorkList() {
        SortedSet<Work<?>> allWork = new TreeSet<>();

        // Collect all Jobs (ServiceTask-related work)
        // Therefore activate/load all new jobs
        ActivateJobsResponse jobsResponse = zeebeClient.newActivateJobsCommand().jobType(DemoModelInstrumentator.DEMO_WORKER_TYPE)
                .maxJobsToActivate(Integer.MAX_VALUE)
                .timeout(Duration.ofDays(1000000))
                .workerName("demo-data-generator")
                .send().join();
        jobsForWork.addAll(jobsResponse.getJobs());

        // and add all jobs to work list which is sorted by date
        for (ActivatedJob job : jobsForWork) {
            allWork.add(new JobWork(job, tweakedBpmnModelInstance, zeebeClient, jobsForWork));
        }

        // Collect all Human Tasks (User Task related work)
        // TODO Query User Tasks and create WorkItem as above

        // TODO: How to handle messages?

        return allWork;
    }


}


