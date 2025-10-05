package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LocationPointsSimplificationService {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(LocationPointsSimplificationService.class);

    /**
     * Simplify a list of location points using the Visvalingam-Whyatt algorithm
     *
     * @param points The original list of points
     * @param zoom   The current map zoom level (null means no simplification)
     * @return Simplified list of points
     */
    public List<LocationPoint> simplifyPoints(List<LocationPoint> points, Integer zoom) {
        // If zoom is not provided or points are too few, return original
        if (zoom == null || points.size() <= 2) {
            return points;
        }

        // Calculate target point count based on zoom level
        // Higher zoom = more detail = more points
        // Zoom levels typically range from 1-20
        int targetPointCount = calculateTargetPointCount(points.size(), zoom);


        if (targetPointCount >= points.size()) {
            return points;
        }

        logger.debug("Simplifying {} points to {} points for zoom level {}", points.size(), targetPointCount, zoom);

        return visvalingamWhyatt(points, targetPointCount);
    }

    /**
     * Calculate target point count based on zoom level
     */
    private int calculateTargetPointCount(int originalCount, int zoom) {
        // Ensure we keep at least 10 points and at most the original count
        // Zoom 1-5: very simplified (10-20% of points)
        // Zoom 6-10: moderately simplified (20-40% of points)
        // Zoom 11-15: lightly simplified (40-70% of points)
        // Zoom 16+: minimal simplification (70-100% of points)

        double retentionRatio;
        if (zoom <= 13) {
            retentionRatio = 0.10 + (zoom - 1) * 0.025; // 10% to 20%
        } else if (zoom <= 15) {
            retentionRatio = 0.20 + (zoom - 6) * 0.04; // 20% to 40%
        } else if (zoom <= 18) {
            retentionRatio = 0.40 + (zoom - 11) * 0.06; // 40% to 70%
        } else {
            retentionRatio = 0.70 + Math.min(zoom - 16, 4) * 0.075; // 70% to 100%
        }


        int targetCount = (int) Math.ceil(originalCount * retentionRatio);
        targetCount = Math.min(3000, targetCount);
        return Math.max(10, Math.min(targetCount, originalCount));
    }

    /**
     * Visvalingam-Whyatt algorithm implementation for polyline simplification
     */
    private List<LocationPoint> visvalingamWhyatt(List<LocationPoint> points, int targetCount) {
        if (points.size() <= targetCount) {
            return points;
        }

        // Create a list of triangles with their effective areas
        List<Triangle> triangles = new ArrayList<>();

        // Initialize triangles for all interior points
        for (int i = 1; i < points.size() - 1; i++) {
            Triangle triangle = new Triangle(i - 1, i, i + 1, points);
            triangles.add(triangle);
        }

        // Use a priority queue to efficiently find the triangle with minimum area
        PriorityQueue<Triangle> heap = new PriorityQueue<>(Comparator.comparingDouble(t -> t.area));
        heap.addAll(triangles);

        // Track which points to keep
        Set<Integer> removedIndices = new HashSet<>();

        // Remove points until we reach the target count
        int pointsToRemove = points.size() - targetCount;

        while (pointsToRemove > 0 && !heap.isEmpty()) {
            Triangle minTriangle = heap.poll();

            // Skip if this triangle's center point was already removed
            if (removedIndices.contains(minTriangle.centerIndex)) {
                continue;
            }

            // Mark the center point for removal
            removedIndices.add(minTriangle.centerIndex);
            pointsToRemove--;

            // Update neighboring triangles
            updateNeighboringTriangles(heap, minTriangle, removedIndices, points);
        }

        // Build the result list with remaining points
        List<LocationPoint> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (!removedIndices.contains(i)) {
                result.add(points.get(i));
            }
        }

        return result;
    }

    /**
     * Update neighboring triangles after removing a point
     */
    private void updateNeighboringTriangles(PriorityQueue<Triangle> heap, Triangle removed,
                                            Set<Integer> removedIndices, List<LocationPoint> points) {
        // Find the previous and next non-removed points
        int prevIndex = removed.leftIndex;
        while (prevIndex > 0 && removedIndices.contains(prevIndex)) {
            prevIndex--;
        }

        int nextIndex = removed.rightIndex;
        while (nextIndex < points.size() - 1 && removedIndices.contains(nextIndex)) {
            nextIndex++;
        }

        // Create new triangles if possible
        if (prevIndex > 0 && !removedIndices.contains(prevIndex - 1)) {
            Triangle newTriangle = new Triangle(prevIndex - 1, prevIndex, nextIndex, points);
            heap.add(newTriangle);
        }

        if (nextIndex < points.size() - 1 && !removedIndices.contains(nextIndex + 1)) {
            Triangle newTriangle = new Triangle(prevIndex, nextIndex, nextIndex + 1, points);
            heap.add(newTriangle);
        }
    }

    /**
     * Helper class to represent a triangle formed by three consecutive points
     */
    private static class Triangle {
        final int leftIndex;
        final int centerIndex;
        final int rightIndex;
        final double area;

        Triangle(int leftIndex, int centerIndex, int rightIndex, List<LocationPoint> points) {
            this.leftIndex = leftIndex;
            this.centerIndex = centerIndex;
            this.rightIndex = rightIndex;
            this.area = calculateTriangleArea(
                    points.get(leftIndex),
                    points.get(centerIndex),
                    points.get(rightIndex)
            );
        }

        /**
         * Calculate the area of a triangle formed by three points using the cross product
         */
        private static double calculateTriangleArea(LocationPoint p1,
                                                    LocationPoint p2,
                                                    LocationPoint p3) {
            // Using the cross product formula for triangle area
            // Area = 0.5 * |x1(y2 - y3) + x2(y3 - y1) + x3(y1 - y2)|
            double area = Math.abs(
                    p1.getLongitude() * (p2.getLatitude() - p3.getLatitude()) +
                            p2.getLongitude() * (p3.getLatitude() - p1.getLatitude()) +
                            p3.getLongitude() * (p1.getLatitude() - p2.getLatitude())
            ) / 2.0;

            return area;
        }
    }

}
