package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TransportModeClassifier {

    private final ChunkFeatureExtractor featureExtractor;
    private final ViterbiDecoder decoder;

    public TransportModeClassifier(ChunkFeatureExtractor featureExtractor, ViterbiDecoder decoder) {
        this.featureExtractor = featureExtractor;
        this.decoder = decoder;
    }

    public List<TransportModeSegment> classify(List<RawLocationPoint> points, Instant first, Instant last,
                                               List<TransportModeConfig> configs, double sensitivity) {
        if (points.size() < 2 || configs.isEmpty()) {
            return List.of();
        }

        List<ChunkFeatures> chunks = featureExtractor.extract(points, first, last);
        if (chunks.isEmpty()) {
            return List.of();
        }

        ViterbiDecoder.DecodeResult decoded = decoder.decode(chunks, configs, sensitivity);
        List<TransportModeSegment> segments = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            ChunkFeatures chunk = chunks.get(index);
            segments.add(new TransportModeSegment(
                    decoded.modes().get(index),
                    chunk.startOffsetSeconds(),
                    chunk.durationSeconds(),
                    chunk.distanceMeters(),
                    confidence(decoded, index, chunk)
            ));
        }
        return segments;
    }

    private double confidence(ViterbiDecoder.DecodeResult decoded, int index, ChunkFeatures chunk) {
        if (!chunk.reliable()) {
            return 0.0;
        }
        double posterior = decoded.posteriors()[index][decoded.states()[index]];
        double fit = Math.min(1.0, decoded.fit()[index] / 0.3);
        double dataQuality = Math.min(1.0, chunk.pointCount() / 4.0);
        return Math.max(0.0, Math.min(1.0, posterior * fit * dataQuality));
    }
}
