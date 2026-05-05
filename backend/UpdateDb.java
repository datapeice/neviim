import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class UpdateDb {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:h2:file:./data/neviim-db", "SA", "");
        Statement stmt = conn.createStatement();
        int count = stmt.executeUpdate("UPDATE points SET country_code = 'PL' WHERE country_code = '??'");
        System.out.println("Updated " + count + " points");
        conn.close();
    }
}
