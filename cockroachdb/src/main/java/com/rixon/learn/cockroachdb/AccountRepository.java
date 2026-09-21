package com.rixon.learn.cockroachdb;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Repository
@Transactional
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    @Query(value = "select balance from Account where id=?1")
    BigDecimal getBalance(Long id);

    @Query(value = "select balance from Account where id=?1")
    @Lock(LockModeType.PESSIMISTIC_READ)
    BigDecimal getBalanceForShare(Long id);

    @Modifying
    @Query("update Account set balance = balance + ?2 where id=?1")
    void updateBalance(Long id, BigDecimal balance);
}
