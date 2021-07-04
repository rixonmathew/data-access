package com.rixon.learn.spring.data.dataaccess.cassandra;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DataService {

    @Autowired
    private ContractCassRepository contractCassRepository;

    @GetMapping("/contracts")
    public List<ContractCassandraNonReactive> allContracts() {
        return contractCassRepository.findAll();
    }
}
