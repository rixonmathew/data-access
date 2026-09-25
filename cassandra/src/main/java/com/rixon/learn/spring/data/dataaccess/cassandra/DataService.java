package com.rixon.learn.spring.data.dataaccess.cassandra;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DataService {

    private final ContractCassRepository contractCassRepository;

    public DataService(ContractCassRepository contractCassRepository) {
        this.contractCassRepository = contractCassRepository;
    }

    @GetMapping("/contracts")
    public List<ContractCassandraNonReactive> allContracts() {
        return contractCassRepository.findAll();
    }
}
