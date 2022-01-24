package com.rixon.learn.cockroachdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SpringBootApplication
public class CockroachDBApplication {

    private final static Logger LOGGER = LoggerFactory.getLogger(CockroachDBApplication.class);
    private static final int LIMIT = 10_000;

    public static void main(String[] args) {
        SpringApplication.run(CockroachDBApplication.class,args);
    }

    @Bean
    CommandLineRunner dataLoader(AccountRepository accountRepository) {
        return args -> {
            LOGGER.info("Checking need for dummy accounts creation");
            long count = accountRepository.count();
            if (count<LIMIT) {
                LOGGER.info("Creating [{}] accounts",LIMIT);
                List<Account> accounts = IntStream.rangeClosed(1, LIMIT)
                        .mapToObj(i -> {
                            Account account = new Account();
                            account.setName("Test" + i);
                            account.setType(AccountType.asset);
                            account.setBalance(new BigDecimal(100 * i));
                            return account;
                        }).collect(Collectors.toList());
                LOGGER.info("Saving [{}] accounts ",accounts.size());
                long startTime = System.currentTimeMillis();
                accountRepository.saveAllAndFlush(accounts);
                LOGGER.info("Saved [{}] accounts in [{}] ms",accounts.size(),System.currentTimeMillis()-startTime);

            } else {
                LOGGER.info("Found [{}] accounts, bypassing account creation",count);
            }
        };

    }
}
