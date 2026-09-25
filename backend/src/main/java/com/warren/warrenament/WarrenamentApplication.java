package com.warren.warrenament;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// Drives LotSweeper, which closes lots whose countdown has expired.
@EnableScheduling
public class WarrenamentApplication {

	public static void main(String[] args) {
		SpringApplication.run(WarrenamentApplication.class, args);
	}

}
