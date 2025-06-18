package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SignificantPlaceJdbcService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public SignificantPlaceJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    private static final RowMapper<SignificantPlace> SIGNIFICANT_PLACE_ROW_MAPPER = new RowMapper<SignificantPlace>() {
        @Override
        public SignificantPlace mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("display_name"),
                rs.getLong("user_version")
            );
            
            return new SignificantPlace(
                rs.getLong("id"),
                user,
                rs.getString("name"),
                rs.getDouble("latitude_centroid"),
                rs.getDouble("longitude_centroid"),
                null, // geom - would need PostGIS handling
                rs.getDouble("radius_meters")
            );
        }
    };
    
    public List<SignificantPlace> findByUser(User user) {
        String sql = "SELECT sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid, sp.radius_meters, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM significant_places sp " +
                    "JOIN users u ON sp.user_id = u.id " +
                    "WHERE sp.user_id = ?";
        return jdbcTemplate.query(sql, SIGNIFICANT_PLACE_ROW_MAPPER, user.getId());
    }
    
    public Page<SignificantPlace> findByUser(User user, Pageable pageable) {
        String countSql = "SELECT COUNT(*) FROM significant_places WHERE user_id = ?";
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, user.getId());
        
        String sql = "SELECT sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid, sp.radius_meters, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM significant_places sp " +
                    "JOIN users u ON sp.user_id = u.id " +
                    "WHERE sp.user_id = ? " +
                    "LIMIT ? OFFSET ?";
        List<SignificantPlace> content = jdbcTemplate.query(sql, SIGNIFICANT_PLACE_ROW_MAPPER, 
            user.getId(), pageable.getPageSize(), pageable.getOffset());
        
        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }
    
    public List<SignificantPlace> findNearbyPlaces(Long userId, Point point, double distanceInMeters) {
        String sql = "SELECT sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid, sp.radius_meters, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM significant_places sp " +
                    "JOIN users u ON sp.user_id = u.id " +
                    "WHERE sp.user_id = ? " +
                    "AND ST_DWithin(sp.geom, ?, ?)";
        return jdbcTemplate.query(sql, SIGNIFICANT_PLACE_ROW_MAPPER, 
            userId, point, distanceInMeters);
    }
    
    public SignificantPlace create(SignificantPlace place) {
        String sql = "INSERT INTO significant_places (user_id, name, latitude_centroid, longitude_centroid, geom, radius_meters) " +
                    "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
            place.getUser().getId(),
            place.getName(),
            place.getLatitudeCentroid(),
            place.getLongitudeCentroid(),
            place.getGeom(), // Would need PostGIS handling
            place.getRadiusMeters()
        );
        return new SignificantPlace(id, place.getUser(), place.getName(),
            place.getLatitudeCentroid(), place.getLongitudeCentroid(),
            place.getGeom(), place.getRadiusMeters());
    }
    
    public SignificantPlace update(SignificantPlace place) {
        String sql = "UPDATE significant_places SET name = ?, latitude_centroid = ?, longitude_centroid = ?, geom = ?, radius_meters = ? WHERE id = ?";
        jdbcTemplate.update(sql,
            place.getName(),
            place.getLatitudeCentroid(),
            place.getLongitudeCentroid(),
            place.getGeom(), // Would need PostGIS handling
            place.getRadiusMeters(),
            place.getId()
        );
        return place;
    }
    
    public Optional<SignificantPlace> findById(Long id) {
        String sql = "SELECT sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid, sp.radius_meters, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM significant_places sp " +
                    "JOIN users u ON sp.user_id = u.id " +
                    "WHERE sp.id = ?";
        List<SignificantPlace> results = jdbcTemplate.query(sql, SIGNIFICANT_PLACE_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public void deleteById(Long id) {
        String sql = "DELETE FROM significant_places WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}
