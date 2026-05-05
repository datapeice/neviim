import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDb {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:h2:file:./data/neviim-db", "SA", "");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT country_code, COUNT(*) FROM points GROUP BY country_code");
        while(rs.next()) {
            System.out.println(rs.getString(1) + " : " + rs.getInt(2));
        }
        conn.close();
    }
}
