package camunda.cloud.simulator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.PostConstruct;

@SpringBootApplication
@EnableZeebeClient
@ZeebeDeployment(resources = "classpath:*bpmn")
public class SimulatorApplication {

	@Autowired
	private ZeebeClient zeebeClient;
	protected final String zeebeBrokerAddress = "123";

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(SimulatorApplication.class, args);
		context.getBean(SimulatorApplication.class).triggerSimulation();
	}

	public void triggerSimulation() {
		System.out.println("------------------ Start of the Simulation has been triggered -----------------");
		BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(this.getClass().getResourceAsStream("/test.bpmn"));
		System.out.println("------------------------ BPMN Model has been imported -------------------------");
		DemoDataGenerator.autoGenerateFor(zeebeClient, zeebeBrokerAddress, bpmnModelInstance);
	}
}
