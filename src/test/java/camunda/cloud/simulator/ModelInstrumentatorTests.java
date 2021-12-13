package camunda.cloud.simulator;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Documentation;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Component
public class ModelInstrumentatorTests {
    BpmnModelInstance loadBPMNModel(String modelName){
        BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(this.getClass().getResourceAsStream("test/"+ modelName +".bpmn"));
        return bpmnModelInstance;
    }

    @Test
    void canTweakProcessDefinition_successfully(){
        //given
        BpmnModelInstance originalModelInstance = loadBPMNModel("test");

        //when
        DemoModelInstrumentator instrumentator = new DemoModelInstrumentator();
        BpmnModelInstance tweakedModelInstance = instrumentator.tweakProcessDefinition(originalModelInstance);

        //then
        assertNotNull(tweakedModelInstance);
    }

    @Test
    void tweakedModel_differsFromOriginalModel(){
        //given
        BpmnModelInstance originalModelInstance = loadBPMNModel("test");

        //when
        DemoModelInstrumentator instrumentator = new DemoModelInstrumentator();
        BpmnModelInstance tweakedModelInstance = instrumentator.tweakProcessDefinition(originalModelInstance);

        //then
        assertFalse(tweakedModelInstance.equals(instrumentator.getOriginalModels()));
    }

    @Test
    void tweakedGateway_simulatesFromOriginalModel() {
        //given
        BpmnModelInstance originalModelInstance = loadBPMNModel("test_xor");

        //when
        DemoModelInstrumentator instrumentator = new DemoModelInstrumentator();
        BpmnModelInstance tweakedModelInstance = instrumentator.tweakProcessDefinition(originalModelInstance);

        //then
        assertTrue(!tweakedModelInstance.equals(instrumentator.getOriginalModels()));

        Collection<Documentation> documentations = tweakedModelInstance.getModelElementsByType(Documentation.class);
        for (Documentation documentation : documentations) {
            if (SequenceFlow.class.isAssignableFrom(documentation.getParentElement().getClass())) {
                String documentationContent = documentation.getRawTextContent();
                assertFalse(documentationContent.contains("probability"));
            }
        }
    }
}
