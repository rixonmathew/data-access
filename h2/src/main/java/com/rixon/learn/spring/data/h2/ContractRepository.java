package com.rixon.learn.spring.data.h2;

import com.rixon.model.contract.Contract;
import org.springframework.data.repository.CrudRepository;

public interface ContractRepository extends CrudRepository<Contract,String> {
}
