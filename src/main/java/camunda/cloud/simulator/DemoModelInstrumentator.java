package camunda.cloud.simulator;

import camunda.cloud.clock.ClockActuatorClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.*;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible to create a tweaked model that will be executed during simulation
 */
public class DemoModelInstrumentator {

    private static final Logger LOG = LoggerFactory.getLogger(DemoModelInstrumentator.class);

    private Map<String, String> originalModels = new HashMap<String, String>();
    private Map<String, String> tweakedModels = new HashMap<String, String>();
    private Set<String> tweakedProcessKeys = new HashSet<>();

    public BpmnModelInstance tweakProcessDefinition(BpmnModelInstance bpmn) {

        // TODO: Workaround - only works with one process in the model (no collaboratuion diagrams)
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
        // do not do a validation here as it caused quite strange trouble
        LOG.debug("-----\n" + originalBpmn + "\n------");

        // remember original / untouched model (to be able to restiore it later)
        originalModels.put(bpmnProcessId + ".bpmn", originalBpmn);

        // set data generator versionTag
        bpmn.getModelElementsByType(Process.class).stream().forEach(p -> p.setAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "versionTag", "demo-data-generator"));

        Collection<ModelElementInstance> serviceTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(ServiceTask.class));
        Collection<ModelElementInstance> sendTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(SendTask.class));
        // Collection<ModelElementInstance> receiveTasks =
        // bpmn.getModelElementsByType(bpmn.getModel().getType(ReceiveTask.class));
        Collection<ModelElementInstance> businessRuleTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(BusinessRuleTask.class));
        // Collection<ModelElementInstance> scriptTasks =
        // bpmn.getModelElementsByType(bpmn.getModel().getType(ScriptTask.class));
        Collection<ModelElementInstance> userTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(UserTask.class));
        Collection<ModelElementInstance> xorGateways = bpmn.getModelElementsByType(bpmn.getModel().getType(ExclusiveGateway.class));
        // Collection<ModelElementInstance> orGateways =
        // bpmn.getModelElementsByType(bpmn.getModel().getType(InclusiveGateway.class));
        // TODO: Do we want o be able to process scripts?
        //Collection<ModelElementInstance> scripts = bpmn.getModelElementsByType(bpmn.getModel().getType(CamundaScript.class));

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
        /*
        for (ModelElementInstance modelElementInstance : businessRuleTasks) {
            BusinessRuleTask businessRuleTask = (BusinessRuleTask) modelElementInstance;
            if (checkKeepLogic(businessRuleTask)) {
                continue;
            }
            businessRuleTask.removeAttributeNs("http://activiti.org/bpmn", "decisionRef"); // DMN
            businessRuleTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "decisionRef"); // DMN
            businessRuleTask.setCamundaClass(null);
            businessRuleTask.removeAttributeNs("http://activiti.org/bpmn", "delegateExpression");
            businessRuleTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "delegateExpression");

            businessRuleTask.setCamundaExpression("#{true}"); // Noop
        }
        */

        /*
        for (ModelElementInstance modelElementInstance : scripts) {
            // find parent base element
            ModelElementInstance parent = modelElementInstance;
            do {
                parent = parent.getParentElement();
            } while (parent != null && !(parent instanceof BaseElement));

            // keep logic only if we found some BaseElement as parent
            if (parent != null && checkKeepLogic((BaseElement) parent)) {
                continue;
            }

            CamundaScript script = (CamundaScript) modelElementInstance;
            // executionListener.setCamundaClass(null);
            script.setTextContent(""); // java.lang.System.out.println('x');
            script.setCamundaScriptFormat("javascript");
            script.removeAttributeNs("http://activiti.org/bpmn", "resource");
            script.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "resource");
        }*/

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
            tweakGateway(xorGateway);
        }

        /*
        for (BaseElement element : elementsWithSetVariable) {
            if (Stream
                    .of(Process.class, Task.class, ServiceTask.class, SendTask.class, UserTask.class, BusinessRuleTask.class, ScriptTask.class, ReceiveTask.class,
                            ManualTask.class, ExclusiveGateway.class, SequenceFlow.class, ParallelGateway.class, InclusiveGateway.class, EventBasedGateway.class,
                            StartEvent.class, IntermediateCatchEvent.class, IntermediateThrowEvent.class, EndEvent.class, BoundaryEvent.class, SubProcess.class,
                            CallActivity.class) //
                    .anyMatch(clazz -> clazz.isInstance(element))) {
                enableSetVariable(element);
            } else {
                LOG.warn("Element '{}' has 'simulateSetVariable' set but allows no execution listeners, Ignoring.", element.getId());
            }
        }
        */

        // Bpmn.validateModel(bpmn);
        String xmlString = Bpmn.convertToString(bpmn);
        tweakedModels.put(bpmnProcessId + ".bpmn", xmlString);
        LOG.debug("-----TWEAKED-----\n-----TWEAKED-----\n-----\n" + xmlString + "\n------");

        return bpmn;
    }

    private void tweakGateway(ExclusiveGateway xorGateway) {
        // TODO
    }

    protected boolean checkKeepLogic(BaseElement bpmnBaseElement) {
        return false;
        //return readCamundaProperty(bpmnBaseElement, "simulateKeepImplementation").orElse("false").toLowerCase().equals("true");
    }
}
