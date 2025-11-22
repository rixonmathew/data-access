package com.rixon.learn.spring.data.h2;

import com.rixon.model.util.DataGeneratorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;



@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {PersistenceTestConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
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
