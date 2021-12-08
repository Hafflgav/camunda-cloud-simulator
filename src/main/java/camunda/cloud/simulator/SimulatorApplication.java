package camunda.cloud.simulator;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableZeebeClient
public class SimulatorApplication {
	public static void main(String[] args) {
		SpringApplication.run(SimulatorApplication.class, args);
	}
}
