package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

@SuppressWarnings("unused")
@Component
public class V83__Add_legacy_photon_instance extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        // we only want to insert the entry when the SystemEnv is set
        String photonBaseUrl = context.getConfiguration().getPlaceholders().get("photon.baseUrl");
        if (StringUtils.hasText(photonBaseUrl)) {
            String sql = "INSERT INTO geocode_services(name, url_template, enabled, type, priority, version) VALUES(?,?,?,?,?,1)";
            try (PreparedStatement insertStmt = context.getConnection().prepareStatement(sql)) {
                insertStmt.setString(1, "Photon");
                insertStmt.setString(2, photonBaseUrl + "/reverse?lon={lng}&lat={lat}&limit=10&layer=house&layer=locality&radius=0.03");
                insertStmt.setBoolean(3, true);
                insertStmt.setString(4, "PHOTON");
                insertStmt.setInt(5, 1);
                insertStmt.executeUpdate();
            }
        }
    }
}