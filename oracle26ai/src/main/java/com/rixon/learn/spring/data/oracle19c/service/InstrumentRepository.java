package com.rixon.learn.spring.data.oracle19c.service;

import com.rixon.model.instrument.Instrument;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;


public interface InstrumentRepository extends CrudRepository<Instrument,Long> {
     @Query(value = "select count(1) from instrument",nativeQuery = true)
    int getCount();
}
