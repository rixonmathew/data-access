package com.rixon.learn.spring.data.repository;

import com.rixon.learn.spring.data.models.AccountNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountNodeRepository extends Neo4jRepository<AccountNode, String> {

    @Query("MATCH (start:Account {accountNumber: $sourceAcc}), (target:Account {accountNumber: $targetAcc}), " +
           "p = shortestPath((start)-[:HAS_EXPOSURE_TO*..10]-(target)) " +
           "RETURN [n IN nodes(p) | n.accountNumber]")
    List<String> findShortestRiskPath(@Param("sourceAcc") String sourceAcc, @Param("targetAcc") String targetAcc);

    @Query("MATCH path = (a:Account {accountNumber: $accountNumber})-[:HAS_EXPOSURE_TO*2..6]->(a) " +
           "UNWIND nodes(path) AS n " +
           "RETURN DISTINCT n.accountNumber")
    List<String> findCyclicRiskExposure(@Param("accountNumber") String accountNumber);

    @Query("MATCH (a:Account {accountNumber: $accountNumber})-[:HAS_EXPOSURE_TO*1..3]->(downstream:Account) " +
           "RETURN DISTINCT downstream.accountNumber")
    List<String> findDownstreamContagionAccounts(@Param("accountNumber") String accountNumber);
}
