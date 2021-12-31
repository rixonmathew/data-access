package com.rixon.model.util;

import com.rixon.model.contract.Contract;
import com.rixon.model.instrument.Instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public static List<Instrument> randomInstruments(long count) {
        return LongStream.range(0,count)
                .parallel()
                .mapToObj(DataGeneratorUtils::randomInstrument)
                .collect(Collectors.toList());
    }

    private static  Instrument randomInstrument(long id) {
        Instrument instrument = new Instrument();
        instrument.setId(id);
        instrument.setName("AB CORP SHARES");
        instrument.setType("BOND");
        instrument.setMetadata("{'inceptionDate':'01-Jan-1908','rating':'BB+'}");
        return instrument;
    }
}
