package camunda.cloud.simulator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.PostConstruct;

@SpringBootApplication
@EnableZeebeClient
@ZeebeDeployment(resources = "classpath:*bpmn")
public class SimulatorApplication {

	@Autowired
	private ZeebeClient client;

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(SimulatorApplication.class, args);
		context.getBean(SimulatorApplication.class).doSomeSampleStuff();
	}

	public void doSomeSampleStuff() {
		System.out.println("JIIIIIHAAAA ##############################################");

		// for the moment just read if from classpath
		BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(this.getClass().getResourceAsStream("/test.bpmn"));

		// but deploy to Zeebe to "simulate" real situation
		//client.newDeployCommand().addProcessModel(bpmnModelInstance, "test.bpmn").send().join();

		// but now generate some demo data
		DemoDataGenerator.autoGenerateFor(bpmnModelInstance);
	}
}
