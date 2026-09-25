package com.warren.warrenament;

import org.springframework.boot.SpringApplication;

public class TestWarrenamentApplication {

	public static void main(String[] args) {
		SpringApplication.from(WarrenamentApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
