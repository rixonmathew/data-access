package com.rixon.model.util;

import com.rixon.model.contract.Contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class DataGeneratorUtils {

    public static List<Contract> randomContracts(long count){
        return LongStream.range(0,count)
                .mapToObj(DataGeneratorUtils::randomContract)
                .collect(Collectors.toList());
    }

    private static Contract randomContract(long index) {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID().toString());
        contract.setType("LOAN");
        contract.setAssetIdentifierType("CUSIP");
        contract.setAssetIdentifier("C1123323");
        contract.setQuantity(BigDecimal.valueOf(100));
        contract.setTradeDate(LocalDate.now());
        contract.setSettlementDate(LocalDate.now().plusDays(3));
        contract.setComments(String.valueOf(index));
        return contract;
    }
}
