package com.rixon.learn.spring.data.trino.service;

import com.rixon.learn.spring.data.trino.model.FederatedOrderExposure;
import com.rixon.learn.spring.data.trino.model.TickerAggregatedRisk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FederatedQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Lists all registered catalogs discovered by the Trino coordinator.
     */
    public List<String> listCatalogs() {
        return jdbcTemplate.query("SHOW CATALOGS", (rs, rowNum) -> rs.getString(1));
    }

    /**
     * Initializes reference tables in Trino's high-speed In-Memory catalog.
     */
    public void initializeMemoryCatalog() {
        log.info("Creating memory.default.fx_rates table in Trino Memory catalog...");
        jdbcTemplate.execute("DROP TABLE IF EXISTS memory.default.fx_rates");
        jdbcTemplate.execute("CREATE TABLE memory.default.fx_rates (" +
                "currency VARCHAR, " +
                "fx_rate DECIMAL(18, 6), " +
                "last_updated TIMESTAMP(3)" +
                ")");

        // Insert reference currency conversion rates to USD
        String insertSql = """
            INSERT INTO memory.default.fx_rates VALUES
            ('USD', DECIMAL '1.000000', current_timestamp),
            ('EUR', DECIMAL '1.085000', current_timestamp),
            ('GBP', DECIMAL '1.295000', current_timestamp),
            ('JPY', DECIMAL '0.006700', current_timestamp)
            """;
        jdbcTemplate.execute(insertSql);
        log.info("Inserted FX currency reference data into memory.default.fx_rates");
    }

    /**
     * Executes a cross-engine distributed JOIN across:
     * - postgresql.public.orders (Relational OLTP transactions)
     * - postgresql.public.accounts (Relational customer account data)
     * - memory.default.fx_rates (Trino in-memory reference data)
     */
    public List<FederatedOrderExposure> queryFederatedOrderExposures() {
        String federatedSql = """
            SELECT 
                o.order_id,
                o.account_number,
                a.account_name,
                a.tier,
                o.ticker,
                o.side,
                CAST(o.price AS DECIMAL(18, 4)) AS price,
                CAST(o.quantity AS DECIMAL(18, 4)) AS quantity,
                o.currency,
                fx.fx_rate,
                CAST(o.price * o.quantity * fx.fx_rate AS DECIMAL(18, 4)) AS notional_usd
            FROM postgresql.public.orders o
            JOIN postgresql.public.accounts a ON o.account_number = a.account_number
            JOIN memory.default.fx_rates fx ON o.currency = fx.currency
            ORDER BY notional_usd DESC
            """;

        return jdbcTemplate.query(federatedSql, (rs, rowNum) -> FederatedOrderExposure.builder()
                .orderId(rs.getString("order_id"))
                .accountNumber(rs.getString("account_number"))
                .accountName(rs.getString("account_name"))
                .tier(rs.getString("tier"))
                .ticker(rs.getString("ticker"))
                .side(rs.getString("side"))
                .price(rs.getBigDecimal("price"))
                .quantity(rs.getBigDecimal("quantity"))
                .currency(rs.getString("currency"))
                .fxRateToUsd(rs.getBigDecimal("fx_rate"))
                .notionalUsd(rs.getBigDecimal("notional_usd"))
                .build());
    }

    /**
     * Aggregates portfolio market risk exposure by ticker across catalogs,
     * pushing predicates down into PostgreSQL while aggregating in Trino.
     */
    public List<TickerAggregatedRisk> queryAggregatedRiskByTicker(String tier) {
        String querySql = """
            SELECT 
                o.ticker,
                COUNT(o.order_id) AS total_orders,
                CAST(SUM(o.quantity) AS DECIMAL(18, 4)) AS total_quantity,
                CAST(SUM(o.price * o.quantity * fx.fx_rate) AS DECIMAL(18, 4)) AS total_notional_usd,
                CAST(AVG(o.price) AS DECIMAL(18, 4)) AS average_price
            FROM postgresql.public.orders o
            JOIN postgresql.public.accounts a ON o.account_number = a.account_number
            JOIN memory.default.fx_rates fx ON o.currency = fx.currency
            WHERE a.tier = ?
            GROUP BY o.ticker
            ORDER BY total_notional_usd DESC
            """;

        return jdbcTemplate.query(querySql, (rs, rowNum) -> TickerAggregatedRisk.builder()
                .ticker(rs.getString("ticker"))
                .totalOrders(rs.getLong("total_orders"))
                .totalQuantity(rs.getBigDecimal("total_quantity"))
                .totalNotionalUsd(rs.getBigDecimal("total_notional_usd"))
                .averagePrice(rs.getBigDecimal("average_price"))
                .build(), tier);
    }

    /**
     * Executes queries against Trino's built-in TPC-H analytical benchmark dataset.
     */
    public List<Map<String, Object>> queryTpchNationBenchmark(String regionName) {
        String tpchSql = """
            SELECT 
                n.name AS nation_name,
                r.name AS region_name,
                n.comment AS nation_comment
            FROM tpch.tiny.nation n
            JOIN tpch.tiny.region r ON n.regionkey = r.regionkey
            WHERE r.name = ?
            ORDER BY n.name ASC
            """;

        return jdbcTemplate.queryForList(tpchSql, regionName);
    }

    /**
     * Explains the distributed query execution plan to inspect connector pushdown.
     */
    public String explainQueryPlan(String sql) {
        List<String> planLines = jdbcTemplate.query("EXPLAIN " + sql, (rs, rowNum) -> rs.getString(1));
        return String.join("\n", planLines);
    }
}
