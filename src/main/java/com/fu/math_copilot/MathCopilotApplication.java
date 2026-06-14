package com.fu.math_copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MathCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(MathCopilotApplication.class, args);
	}

}
