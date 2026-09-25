package com.rixon.learn.spring.data.tidb.service;

import com.rixon.learn.spring.data.tidb.model.PortfolioRiskExposure;
import com.rixon.learn.spring.data.tidb.model.TiDbOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

@Service
public class TiDbService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiDbService.class);

    private final JdbcTemplate jdbcTemplate;

    public TiDbService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        LOGGER.info("Initializing TiDB schema and indexes...");
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                }
            }
            LOGGER.info("TiDB schema initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize TiDB schema: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void truncateOrders() {
        jdbcTemplate.execute("DELETE FROM orders;");
    }

    public void createOrder(TiDbOrder order) {
        String sql = "INSERT INTO orders (order_id, account_id, symbol, side, order_type, price, quantity, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                order.getOrderId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide(),
                order.getOrderType(),
                order.getPrice(),
                order.getQuantity(),
                order.getStatus(),
                Timestamp.from(order.getCreatedAt()));
    }

    public void batchCreateOrders(List<TiDbOrder> orders) {
        String sql = "INSERT INTO orders (order_id, account_id, symbol, side, order_type, price, quantity, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, orders, orders.size(), (ps, order) -> {
            ps.setString(1, order.getOrderId());
            ps.setString(2, order.getAccountId());
            ps.setString(3, order.getSymbol());
            ps.setString(4, order.getSide());
            ps.setString(5, order.getOrderType());
            ps.setBigDecimal(6, order.getPrice());
            ps.setLong(7, order.getQuantity());
            ps.setString(8, order.getStatus());
            ps.setTimestamp(9, Timestamp.from(order.getCreatedAt()));
        });
        LOGGER.info("Batch inserted {} orders into TiDB.", orders.size());
    }

    public void updateOrderStatus(String orderId, String newStatus) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        jdbcTemplate.update(sql, newStatus, orderId);
    }

    public TiDbOrder getOrder(String orderId) {
        String sql = "SELECT order_id, account_id, symbol, side, order_type, price, quantity, status, created_at FROM orders WHERE order_id = ?";
        List<TiDbOrder> orders = jdbcTemplate.query(sql, (rs, rowNum) -> TiDbOrder.builder()
                .orderId(rs.getString("order_id"))
                .accountId(rs.getString("account_id"))
                .symbol(rs.getString("symbol"))
                .side(rs.getString("side"))
                .orderType(rs.getString("order_type"))
                .price(rs.getBigDecimal("price"))
                .quantity(rs.getLong("quantity"))
                .status(rs.getString("status"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .build(), orderId);
        return orders.isEmpty() ? null : orders.get(0);
    }

    public List<PortfolioRiskExposure> getPortfolioRiskExposure(String accountId) {
        String sql = "SELECT account_id, " +
                     "       symbol, " +
                     "       SUM(CASE WHEN side = 'BUY' THEN quantity ELSE -quantity END) as net_position, " +
                     "       SUM(quantity) as gross_volume, " +
                     "       SUM(price * quantity) as gross_notional, " +
                     "       SUM(CASE WHEN side = 'BUY' THEN price * quantity ELSE 0 END) / NULLIF(SUM(CASE WHEN side = 'BUY' THEN quantity ELSE 0 END), 0) as vwap_buy, " +
                     "       SUM(CASE WHEN side = 'SELL' THEN price * quantity ELSE 0 END) / NULLIF(SUM(CASE WHEN side = 'SELL' THEN quantity ELSE 0 END), 0) as vwap_sell " +
                     "FROM orders " +
                     "WHERE account_id = ? AND status = 'FILLED' " +
                     "GROUP BY account_id, symbol " +
                     "ORDER BY symbol";

        return jdbcTemplate.query(sql, (rs, rowNum) -> PortfolioRiskExposure.builder()
                .accountId(rs.getString("account_id"))
                .symbol(rs.getString("symbol"))
                .netPosition(rs.getLong("net_position"))
                .grossVolume(rs.getLong("gross_volume"))
                .grossNotional(rs.getBigDecimal("gross_notional"))
                .vwapBuy(rs.getBigDecimal("vwap_buy"))
                .vwapSell(rs.getBigDecimal("vwap_sell"))
                .build(), accountId);
    }
}
