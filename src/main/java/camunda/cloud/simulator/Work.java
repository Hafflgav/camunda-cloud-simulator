package camunda.cloud.simulator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.util.*;


abstract class Work<T> implements Comparable<Work<T>> {

    private static final Logger LOG = LoggerFactory.getLogger(TimeAwareDemoGenerator.class);

    protected T workItem;
    protected BpmnModelInstance bpmn;

    private static Map<Object, Date> dueCache = new HashMap<>();
    private static Map<String, StatisticalDistribution> distributions = new HashMap<String, StatisticalDistribution>();

    private final DatatypeFactory datatypeFactory;

    public Work(T workItem, BpmnModelInstance bpmn) {
        this.workItem = workItem;
        this.bpmn = bpmn;

        try {
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public void execute() {
        executeImpl();

        // If we are a recurring job, we have to make sure that due date is newly
        // calculated after each execution. To do so, we simply remove it from
        // cache in every case.
        dueCache.remove(workItem);
    };

    abstract protected void executeImpl();

    abstract protected String getElementId();
    abstract protected Date getElementCreationTime();

    public int compareTo(Work<T> other) {
        return this.getDue().compareTo(other.getDue());
    }

    protected T getWorkItem() {
        return workItem;
    }

    public Date getDue() {
        if (!dueCache.containsKey(workItem)) {
            dueCache.put(workItem, calculateNewRandomDue());
        }
        return dueCache.get(workItem);
    }

    protected Date calculateNewRandomDue() {
        if (!distributions.containsKey(getElementId())) {
            StatisticalDistribution distribution = createDistributionForElement(getElementId());
            distributions.put(getElementId(), distribution);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(getElementCreationTime());
        double timeToWait = distributions.get(getElementId()).nextSample();
        if (timeToWait <= 0) {
            timeToWait = 1;
        }
        cal.add(Calendar.SECOND, (int) Math.round(timeToWait));

        return cal.getTime();
    }

    protected StatisticalDistribution createDistributionForElement(String elementId) {
        try {
            BaseElement taskElement = bpmn.getModelElementById(elementId);

            // Default = 10 minutes each
            double durationMean = DemoDataGenerator.findProperty(bpmn, taskElement, "durationMean").flatMap(this::parseTime).orElse(600.0);
            double durationStandardDeviation = DemoDataGenerator.findProperty(bpmn, taskElement, "durationSd").flatMap(this::parseTime).orElse(0.0);

            StatisticalDistribution distribution = new StatisticalDistribution(durationMean, durationStandardDeviation);
            return distribution;
        } catch (Exception ex) {
            throw new RuntimeException("Could not read distribution for element '" + elementId + "' of process definition '" + bpmn + "'", ex);
        }
    }


    private Optional<Double> parseTime(String time) {
        if (time.startsWith("P")) {
            try {
                Duration duration = datatypeFactory.newDuration(time);
                // okay, months with fixed 30 days is somewhat whacky - who cares
                Double seconds = ((((duration.getYears() * 12 //
                        + duration.getMonths()) * 30 //
                        + duration.getDays()) * 24 //
                        + duration.getHours()) * 60 //
                        + duration.getMinutes()) * 60 //
                        + Optional.ofNullable(duration.getField(DatatypeConstants.SECONDS)).map(Number::doubleValue).orElse(0.0);
                return Optional.of(Double.valueOf(seconds));
            } catch (Exception e) {
                LOG.error("Cannot parse time: {}", time);
                return Optional.empty();
            }
        } else {
            try {
                return Optional.of(Double.valueOf(time));
            } catch (NumberFormatException e) {
                LOG.error("Cannot parse time: {}", time);
                return Optional.empty();
            }
        }
    }
}