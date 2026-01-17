package com.ticketledger;

import org.springframework.boot.SpringApplication;

public class TestTicketLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(TicketLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
