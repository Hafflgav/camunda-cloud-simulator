package camunda.cloud.simulator;

import camunda.cloud.clock.ClockActuatorClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Documentation;
import io.camunda.zeebe.model.bpmn.instance.Process;

import java.util.*;
import java.util.logging.Logger;

public class DemoDataGenerator {
    public static final String VAR_NAME_GENERATED = "demo-data-generated";
    private static final Logger log = Logger.getLogger(DemoDataGenerator.class.getName());

    public static long autoGenerateAll(ZeebeClient client, String zeebeBrokerAddress, List<BpmnModelInstance> bpmnModelInstances) {
        long startedInstances = 0;
        for (BpmnModelInstance modelInstance : bpmnModelInstances) {
            startedInstances += autoGenerateFor(client, zeebeBrokerAddress, modelInstance);
        }
        return startedInstances;
    }

    public static long autoGenerateFor(ZeebeClient zeebeClient, String zeebeBrokerAddress, BpmnModelInstance modelInstance) {
        log.info("check auto generation for " + modelInstance.getDefinitions().getName());

        String simulate = findProperty(modelInstance, "simulate").orElse("false");
        String numberOfDaysInPast = findProperty(modelInstance, "simulateNumberOfDaysInPast").orElse("30");
        String timeBetweenStartsBusinessDaysMean = findProperty(modelInstance, "simulateTimeBetweenStartsBusinessDaysMean").orElse("3600");
        String timeBetweenStartsBusinessDaysSd = findProperty(modelInstance, "simulateTimeBetweenStartsBusinessDaysSd").orElse("0");
        String startBusinessDayAt = findProperty(modelInstance, "simulateStartBusinessDayAt").orElse("8:00");
        String endBusinessDayAt = findProperty(modelInstance, "simulateEndBusinessDayAt").orElse("18:00");
        String includeWeekend = findProperty(modelInstance, "simulateIncludeWeekend").orElse("false");
        boolean runAlways = findProperty(modelInstance, "simulateRunAlways").orElse("false").toLowerCase().equals("true");

        if (simulate.equals("false")) {
            log.info("simulation was set to false - no simulation triggered");
            return 0;
        } else {
            log.info("simulation properties set - auto generation applied (" + numberOfDaysInPast + " days in past, time between mean: "
                    + timeBetweenStartsBusinessDaysMean + " and Standard Deviation: " + timeBetweenStartsBusinessDaysSd);

            return new TimeAwareDemoGenerator(zeebeClient, modelInstance, new ClockActuatorClient(zeebeBrokerAddress))
                    .processID(modelInstance.getModelElementsByType(Process.class).stream().findFirst().get().getId())
                    .numberOfDaysInPast(Integer.valueOf(numberOfDaysInPast))
                    .timeBetweenStartsBusinessDays(timeBetweenStartsBusinessDaysMean, timeBetweenStartsBusinessDaysSd)
                    .startTimeBusinessDay(startBusinessDayAt)
                    .endTimeBusinessDay(endBusinessDayAt)
                    .includeWeekend(includeWeekend.toLowerCase().equals("true"))
                    .runAlways(runAlways)
                    .run();
        }
    }

    public static Optional<String> findProperty(BpmnModelInstance modelInstance, String propertyNameToSearch) {
        Collection<Documentation> documentations = modelInstance.getModelElementsByType(Documentation.class);
        HashMap<String, String> simulationParameter = new HashMap<>();

        for (Documentation documentation : documentations) {
            String documentationContent = documentation.getRawTextContent();
            String[] lines = documentationContent.split("\n");

            for (String line : lines) {
                if(line.isEmpty()){
                    continue;
                }
                String[] keyValue = line.trim().split("=");
                simulationParameter.put(keyValue[0].trim(), keyValue[1].trim());

                if (simulationParameter.containsKey(propertyNameToSearch)) {
                    return Optional.of(simulationParameter.get(propertyNameToSearch));
                }
            }
        }

        /**
         * Question: Removing the if condition (if (Process.class.isAssignableFrom(documentation.getParentElement().getClass())))
         * should make it possible to query the whole documentation
         */
        return Optional.empty();
    }
}
