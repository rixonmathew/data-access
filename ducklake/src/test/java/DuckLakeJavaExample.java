import java.sql.*;

public class DuckLakeJavaExample {

    public static void main(String[] args) {
        try {
            // Load the DuckDB JDBC driver
            Class.forName("org.duckdb.DuckDBDriver");

            // Create DuckDB connection
            Connection conn = DriverManager.getConnection("jdbc:duckdb:");

            // Install and load required extensions
            executeStatement(conn, "INSTALL ducklake;");
            executeStatement(conn, "LOAD ducklake;");
            executeStatement(conn, "INSTALL postgres;");
            executeStatement(conn, "LOAD postgres;");

            // Create PostgreSQL secret for authentication
            executeStatement(conn,
                    "CREATE SECRET postgres_secret (" +
                            "TYPE POSTGRES, " +
                            "HOST 'localhost', " +
                            "PORT 5432, " +
                            "DATABASE 'ducklake_catalog', " +
                            "USER 'your_username', " +
                            "PASSWORD 'your_password'" +
                            ");");

            // Attach to DuckLake using PostgreSQL as metadata store
            executeStatement(conn,
                    "ATTACH 'ducklake:postgres:dbname=ducklake_catalog host=localhost port=5432 user=your_username password=your_password' " +
                            "AS my_ducklake (DATA_PATH 'data_files/');");

            // Switch to DuckLake database
            executeStatement(conn, "USE my_ducklake;");

            // Create a partitioned table
            executeStatement(conn,
                    "CREATE TABLE sales_data (" +
                            "id INTEGER, " +
                            "product_name VARCHAR, " +
                            "sale_amount DECIMAL(10,2), " +
                            "sale_date DATE, " +
                            "region VARCHAR" +
                            ");");

            // Set up partitioning by year and region
            executeStatement(conn, "ALTER TABLE sales_data SET PARTITIONED BY (year(sale_date), region);");

            // Insert sample data
            insertSampleData(conn);

            // Query the partitioned table
            queryPartitionedTable(conn);

            // Demonstrate time travel functionality
            demonstrateTimeTravel(conn);

            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void executeStatement(Connection conn, String sql) throws SQLException {
        System.out.println("Executing: " + sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
        System.out.println("✓ Success\n");
    }

    private static void insertSampleData(Connection conn) throws SQLException {
        System.out.println("Inserting sample data...");

        String insertSQL =
                "INSERT INTO sales_data VALUES " +
                        "(1, 'Laptop', 1299.99, '2024-01-15', 'North'), " +
                        "(2, 'Mouse', 29.99, '2024-01-16', 'South'), " +
                        "(3, 'Keyboard', 79.99, '2024-02-20', 'East'), " +
                        "(4, 'Monitor', 399.99, '2024-02-21', 'West'), " +
                        "(5, 'Tablet', 599.99, '2024-03-10', 'North'), " +
                        "(6, 'Phone', 899.99, '2024-03-15', 'South'), " +
                        "(7, 'Headphones', 149.99, '2023-12-25', 'East'), " +
                        "(8, 'Speaker', 199.99, '2023-11-30', 'West');";

        executeStatement(conn, insertSQL);
    }

    private static void queryPartitionedTable(Connection conn) throws SQLException {
        System.out.println("Querying partitioned table...");

        // Query with partition pruning (filtering by year and region)
        String query =
                "SELECT region, COUNT(*) as total_sales, SUM(sale_amount) as total_revenue " +
                        "FROM sales_data " +
                        "WHERE year(sale_date) = 2024 AND region = 'North' " +
                        "GROUP BY region;";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            System.out.println("Results for 2024 North region:");
            while (rs.next()) {
                System.out.printf("Region: %s, Sales Count: %d, Total Revenue: %.2f%n",
                        rs.getString("region"),
                        rs.getInt("total_sales"),
                        rs.getDouble("total_revenue"));
            }
        }
        System.out.println();

        // Query all data to show partitioning structure
        String allDataQuery = "SELECT * FROM sales_data ORDER BY sale_date;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(allDataQuery)) {

            System.out.println("All sales data:");
            System.out.printf("%-5s %-12s %-12s %-12s %-8s%n", "ID", "Product", "Amount", "Date", "Region");
            System.out.println("-----------------------------------------------------------");

            while (rs.next()) {
                System.out.printf("%-5d %-12s $%-11.2f %-12s %-8s%n",
                        rs.getInt("id"),
                        rs.getString("product_name"),
                        rs.getDouble("sale_amount"),
                        rs.getDate("sale_date").toString(),
                        rs.getString("region"));
            }
        }
        System.out.println();
    }

    private static void demonstrateTimeTravel(Connection conn) throws SQLException {
        System.out.println("Demonstrating DuckLake time travel...");

        // Check available snapshots
        String snapshotsQuery = "SELECT snapshot_id, snapshot_time FROM ducklake_snapshots('my_ducklake');";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(snapshotsQuery)) {

            System.out.println("Available snapshots:");
            while (rs.next()) {
                System.out.printf("Snapshot ID: %d, Time: %s%n",
                        rs.getLong("snapshot_id"),
                        rs.getTimestamp("snapshot_time"));
            }
        }
        System.out.println();

        // Query data from a specific snapshot (version 1)
        String timeTravel = "SELECT COUNT(*) as record_count FROM sales_data AT (VERSION => 1);";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(timeTravel)) {

            if (rs.next()) {
                System.out.printf("Records in snapshot 1: %d%n", rs.getInt("record_count"));
            }
        }
        System.out.println();
    }
}
