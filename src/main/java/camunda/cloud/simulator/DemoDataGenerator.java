package camunda.cloud.simulator;

import camunda.cloud.clock.ClockActuatorClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class DemoDataGenerator {
    public static final String VAR_NAME_GENERATED = "demo-data-generated";
    private static final Logger log = Logger.getLogger(DemoDataGenerator.class.getName());
    private static final String zeebeBrokerAddress ="123";

    public static long autoGenerateAll(List<BpmnModelInstance> bpmnModelInstances) {
        long startedInstances = 0;
        for (BpmnModelInstance modelInstance : bpmnModelInstances) {
            startedInstances += autoGenerateFor(modelInstance);
        }
        return startedInstances;
    }

    public static long autoGenerateFor(BpmnModelInstance modelInstance) {
        log.info("check auto generation for " + modelInstance.getDefinitions().getName());

        String simulate = findProperty(modelInstance, "simulate").orElse("false");
        String numberOfDaysInPast = findProperty(modelInstance, "simulateNumberOfDaysInPast").orElse("30");
        String timeBetweenStartsBusinessDaysMean = findProperty(modelInstance, "simulateTimeBetweenStartsBusinessDaysMean").orElse("3600");
        String timeBetweenStartsBusinessDaysSd = findProperty(modelInstance, "simulateTimeBetweenStartsBusinessDaysSd").orElse("0");
        String startBusinessDayAt = findProperty(modelInstance, "simulateStartBusinessDayAt").orElse("8:00");
        String endBusinessDayAt = findProperty(modelInstance, "simulateEndBusinessDayAt").orElse("18:00");
        String includeWeekend = findProperty(modelInstance, "simulateIncludeWeekend").orElse("false");
        boolean runAlways = findProperty(modelInstance, "simulateRunAlways").orElse("false").toLowerCase().equals("true");

        if (simulate.equals("false")){
            log.info("simulation was set to false - no simulation triggered");
            return 0;
        }else {
            log.info("simulation properties set - auto generation applied (" + numberOfDaysInPast + " days in past, time between mean: "
                    + timeBetweenStartsBusinessDaysMean + " and Standard Deviation: " + timeBetweenStartsBusinessDaysSd);

            return new TimeAwareDemoGenerator(modelInstance, new ClockActuatorClient(zeebeBrokerAddress))
                    .processDefinitionKey(modelInstance.getDefinitions().getId())
                    .numberOfDaysInPast(Integer.valueOf(numberOfDaysInPast))
                    .timeBetweenStartsBusinessDays(timeBetweenStartsBusinessDaysMean, timeBetweenStartsBusinessDaysSd)
                    .startTimeBusinessDay(startBusinessDayAt)
                    .endTimeBusinessDay(endBusinessDayAt)
                    .includeWeekend(includeWeekend.toLowerCase().equals("true"))
                    .runAlways(runAlways)
                    .run();
        }
    }

    public static Optional<String> findProperty(BpmnModelInstance modelInstance, String propertyName) {

        /**
         * Todo: Wirte a method to find a property in a Zeebe ModelInstance
         * What is the equivalent to the Object CamundaProperties in Zeebe?
         */

        return Optional.empty();
    }
}