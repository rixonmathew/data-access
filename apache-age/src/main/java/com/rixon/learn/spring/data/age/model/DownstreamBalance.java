package com.rixon.learn.spring.data.age.model;

import java.math.BigDecimal;

/** A downstream account from the graph joined with its balance from the relational table. */
public record DownstreamBalance(String accountNumber, int hops, BigDecimal balance) {
}
