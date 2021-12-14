package camunda.cloud.simulator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

import java.util.Date;
import java.util.List;

public class JobWork extends Work<ActivatedJob> {

    private final ZeebeClient client;

    // We need to keep a reference to the list of job, so that we can remove this job from it upon execution
    private List<ActivatedJob> jobsForWork;

    public JobWork(ActivatedJob workItem, BpmnModelInstance bpmn, ZeebeClient client, List<ActivatedJob> jobsForWork) {
        super(workItem, bpmn);
        this.client = client;
        this.jobsForWork = jobsForWork;
    }

    @Override
    protected void executeImpl() {
        client.newCompleteCommand(getWorkItem().getKey()).send().join();
        jobsForWork.remove(getWorkItem());
    }

    @Override
    protected String getElementId() {
        return getWorkItem().getElementId();
    }

    @Override
    protected Date getElementCreationTime() {
        // We do not get any creation or due date from the API
        // so for the moment we just say this happened when we see the job for the first time
        return ClockUtil.getCurrentTime();
    }

}
