package com.rixon.learn.spring.data.h2;

import com.rixon.model.util.DataGeneratorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;


@DataJpaTest
@ContextConfiguration(classes = {PersistenceTestConfig.class})
public class ContractRepositoryTests {

    @Autowired
    private ContractRepository contractRepository;

    @Test
    @DisplayName("Context loads successfully!")
    public void testContextLoads(){
        assertNotNull(contractRepository);
    }

    @Test
    @DisplayName("Contracts saved successfully!")
    public void testSaveContract(){
        contractRepository.saveAll(DataGeneratorUtils.randomContracts(100));
        contractRepository.findAll().forEach(System.out::println);
    }

}
