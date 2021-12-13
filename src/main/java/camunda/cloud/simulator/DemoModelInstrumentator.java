package camunda.cloud.simulator;

import camunda.cloud.clock.ClockActuatorClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.*;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

import static camunda.cloud.simulator.DemoDataGenerator.findProperty;
import static org.camunda.bpm.model.xml.test.AbstractModelElementInstanceTest.modelInstance;


public class DemoModelInstrumentator {

    private static final Logger LOG = LoggerFactory.getLogger(DemoModelInstrumentator.class);
    private Map<String, String> originalModels = new HashMap<String, String>();
    private Map<String, String> tweakedModels = new HashMap<String, String>();
    private Set<String> tweakedProcessKeys = new HashSet<>();

    /**
     * TODO: Workaround - only works with one process in the model
     * Do we want to be able to process: Script tasks, receiving tasks, business rule tasks?
     * Do we want to care about set variables?
     */
    public BpmnModelInstance tweakProcessDefinition(BpmnModelInstance bpmn) {
        String bpmnProcessId = bpmn.getModelElementsByType(Process.class).stream().findFirst().get().getId();
        Process process = bpmn.getModelElementById(bpmnProcessId);
        Collection<StartEvent> startEvents = process.getChildElementsByType(StartEvent.class);
        StartEvent defaultStart = null;

        if (startEvents.size() == 1) {
            defaultStart = startEvents.iterator().next();
        } else {
            List<StartEvent> candidates = startEvents.stream().filter(se -> se.getEventDefinitions().isEmpty() || se.getEventDefinitions().iterator().next() instanceof TimerEventDefinition).collect(Collectors.toList());
            if (candidates.size() == 1) {
                defaultStart = candidates.get(0);
            }
        }
        if (defaultStart == null) {
            throw new RuntimeException("Process with key '" + bpmnProcessId + "' has no default start event.");
        }

        String originalBpmn = IoUtil.convertXmlDocumentToString(bpmn.getDocument());
        LOG.debug("-----\n" + originalBpmn + "\n------");
        originalModels.put(bpmnProcessId + ".bpmn", originalBpmn);

        bpmn.getModelElementsByType(Process.class).stream().forEach(p -> p.setAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "versionTag", "demo-data-generator"));

        Collection<ModelElementInstance> serviceTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(ServiceTask.class));
        Collection<ModelElementInstance> sendTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(SendTask.class));
        Collection<ModelElementInstance> userTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(UserTask.class));
        Collection<ModelElementInstance> xorGateways = bpmn.getModelElementsByType(bpmn.getModel().getType(ExclusiveGateway.class));

        for (ModelElementInstance modelElementInstance : serviceTasks) {
            ServiceTask serviceTask = ((ServiceTask) modelElementInstance);
            if (checkKeepLogic(serviceTask)) {
                continue;
            }
            serviceTask.getSingleExtensionElement(ZeebeTaskDefinition.class).setType("DEMO-WORKER");
        }
        for (ModelElementInstance modelElementInstance : sendTasks) {
            SendTask serviceTask = ((SendTask) modelElementInstance);
            if (checkKeepLogic(serviceTask)) {
                continue;
            }
            serviceTask.getSingleExtensionElement(ZeebeTaskDefinition.class).setType("DEMO-WORKER");
        }

        for (ModelElementInstance modelElementInstance : userTasks) {
            UserTask userTask = ((UserTask) modelElementInstance);
            if (checkKeepLogic(userTask)) {
                continue;
            }
        }

        for (ModelElementInstance modelElementInstance : xorGateways) {
            ExclusiveGateway xorGateway = ((ExclusiveGateway) modelElementInstance);
            if (checkKeepLogic(xorGateway)) {
                continue;
            }
            tweakGateway(xorGateway, bpmn);
        }

        Bpmn.validateModel(bpmn);
        String xmlString = Bpmn.convertToString(bpmn);
        tweakedModels.put(bpmnProcessId + ".bpmn", xmlString);
        LOG.debug("-----TWEAKED-----\n-----TWEAKED-----\n-----\n" + xmlString + "\n------");
        return bpmn;
    }

    private void tweakGateway(ExclusiveGateway xorGateway, BpmnModelInstance bpmn) {
        double probabilitySum = 0;
        // Process Variable used to store sample from distribution to decide for
        // outgoing transition
        String var = "SIM_SAMPLE_VALUE_" + xorGateway.getId().replaceAll("-", "_");

        Collection<SequenceFlow> flows = xorGateway.getOutgoing();
        if (flows.size() > 1) { // if outgoing flows = 1 it is a joining gateway
            for (SequenceFlow sequenceFlow : flows) {
                double probability = Double.parseDouble(findProperty(bpmn, SequenceFlow.class, "probability").orElse("1.0"));
                ConditionExpression conditionExpression = bpmn.newInstance(ConditionExpression.class);
                conditionExpression.setTextContent("=" + var + " >= " + probabilitySum + " && " + var + " < " + (probabilitySum + probability));
                sequenceFlow.setConditionExpression(conditionExpression);

                probabilitySum += probability;
            }
        }
    }

    protected boolean checkKeepLogic(BaseElement bpmnBaseElement) {
        return false;
        //return readCamundaProperty(bpmnBaseElement, "simulateKeepImplementation").orElse("false").toLowerCase().equals("true");
    }

    public BpmnModelInstance getOriginalModels() {
        LOG.info("Starting to restore models after demo data generation");

        List<BpmnModelInstance> originalModelInstances = new ArrayList<>();
        try {
            for (Map.Entry<String, String> model : originalModels.entrySet()) {
                originalModelInstances.add(Bpmn.readModelFromStream(new ByteArrayInputStream(model.getValue().getBytes("UTF-8"))));
            }
            LOG.info("Restored original modes after demo data generation");
        } catch (Exception ex) {
            throw new RuntimeException("Could not restore original models", ex);
        }
        return originalModelInstances.stream().findFirst().get();
    }
}
