package com.rixon.learn.cockroachdb;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@Repository
@Transactional(propagation = MANDATORY)
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {
    @Query(value = "select balance from Account where id=?1")
    @Lock(LockModeType.PESSIMISTIC_READ)
    BigDecimal getBalance(Long id);

    @Modifying
    @Query("update Account set balance = balance + ?2 where id=?1")
    void updateBalance(Long id, BigDecimal balance);
}
