package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

@SuppressWarnings("unused")
public class V42__Clear_default_admin_password extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        // Check if there's an admin user with the default "admin" password
        String selectSql = "SELECT id, password FROM users WHERE role = 'ADMIN'";

        try (PreparedStatement selectStmt = context.getConnection().prepareStatement(selectSql);
             ResultSet rs = selectStmt.executeQuery()) {

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            while (rs.next()) {
                Long userId = rs.getLong("id");
                String currentPassword = rs.getString("password");

                // If the password matches "admin", clear it
                if (currentPassword != null && encoder.matches("admin", currentPassword)) {
                    String updateSql = "UPDATE users SET password = NULL WHERE id = ?";
                    try (PreparedStatement updateStmt = context.getConnection().prepareStatement(updateSql)) {
                        updateStmt.setLong(1, userId);
                        updateStmt.executeUpdate();
                    }
                }
            }
        }
    }
}