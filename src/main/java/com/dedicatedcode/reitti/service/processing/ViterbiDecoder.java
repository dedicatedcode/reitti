package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ViterbiDecoder {

    private static final double BAND_PENALTY = 0.5;
    private static final double NEGATIVE_INFINITY = Double.NEGATIVE_INFINITY;

    private final ModeEmissionModel emissionModel;

    public ViterbiDecoder(ModeEmissionModel emissionModel) {
        this.emissionModel = emissionModel;
    }

    public DecodeResult decode(List<ChunkFeatures> chunks, List<TransportModeConfig> configs, double sensitivity) {
        int chunkCount = chunks.size();
        int modeCount = configs.size();
        if (chunkCount == 0 || modeCount == 0) {
            return new DecodeResult(new int[0], List.of(), new double[0][0], new double[0]);
        }

        double[][] emissions = emissionLogProbabilities(chunks, configs);
        double[][] transitions = transitionLogProbabilities(configs, sensitivity);
        double[] fit = fit(chunks, configs);

        double[][] delta = new double[chunkCount][modeCount];
        int[][] psi = new int[chunkCount][modeCount];
        for (int mode = 0; mode < modeCount; mode++) {
            delta[0][mode] = Math.log(1.0 / modeCount) + emissions[0][mode];
        }
        for (int chunk = 1; chunk < chunkCount; chunk++) {
            for (int mode = 0; mode < modeCount; mode++) {
                double best = NEGATIVE_INFINITY;
                int bestPrevious = 0;
                for (int previous = 0; previous < modeCount; previous++) {
                    double candidate = delta[chunk - 1][previous] + transitions[previous][mode];
                    if (candidate > best) {
                        best = candidate;
                        bestPrevious = previous;
                    }
                }
                delta[chunk][mode] = best + emissions[chunk][mode];
                psi[chunk][mode] = bestPrevious;
            }
        }

        int[] states = new int[chunkCount];
        states[chunkCount - 1] = argmax(delta[chunkCount - 1]);
        for (int chunk = chunkCount - 2; chunk >= 0; chunk--) {
            states[chunk] = psi[chunk + 1][states[chunk + 1]];
        }

        double[][] posteriors = forwardBackward(emissions, transitions);
        List<TransportMode> modes = new ArrayList<>(chunkCount);
        for (int state : states) {
            modes.add(configs.get(state).mode());
        }
        return new DecodeResult(states, modes, posteriors, fit);
    }

    private double[] fit(List<ChunkFeatures> chunks, List<TransportModeConfig> configs) {
        double[] fit = new double[chunks.size()];
        for (int chunk = 0; chunk < chunks.size(); chunk++) {
            double best = NEGATIVE_INFINITY;
            for (TransportModeConfig config : configs) {
                best = Math.max(best, emissionModel.logMembership(chunks.get(chunk), config));
            }
            fit[chunk] = Math.exp(best);
        }
        return fit;
    }

    private double[][] emissionLogProbabilities(List<ChunkFeatures> chunks, List<TransportModeConfig> configs) {
        double[][] emissions = new double[chunks.size()][configs.size()];
        for (int chunk = 0; chunk < chunks.size(); chunk++) {
            for (int mode = 0; mode < configs.size(); mode++) {
                emissions[chunk][mode] = emissionModel.logLikelihood(chunks.get(chunk), configs.get(mode));
            }
            normalizeRow(emissions[chunk]);
        }
        return emissions;
    }

    private double[][] transitionLogProbabilities(List<TransportModeConfig> configs, double sensitivity) {
        int modeCount = configs.size();
        if (modeCount == 1) {
            return new double[][]{{0.0}};
        }
        int[] bands = bands(configs);
        double switchProbability = Math.min(0.5, Math.exp(-sensitivity));
        double[][] transitions = new double[modeCount][modeCount];
        for (int from = 0; from < modeCount; from++) {
            double weightSum = 0;
            for (int to = 0; to < modeCount; to++) {
                if (to != from) {
                    weightSum += Math.exp(-BAND_PENALTY * Math.abs(bands[from] - bands[to]));
                }
            }
            for (int to = 0; to < modeCount; to++) {
                if (to == from) {
                    transitions[from][to] = Math.log(1.0 - switchProbability);
                } else {
                    double weight = Math.exp(-BAND_PENALTY * Math.abs(bands[from] - bands[to]));
                    transitions[from][to] = Math.log(switchProbability * weight / weightSum);
                }
            }
        }
        return transitions;
    }

    private int[] bands(List<TransportModeConfig> configs) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(midpoint(configs.get(a)), midpoint(configs.get(b))));
        int[] bands = new int[configs.size()];
        for (int band = 0; band < order.size(); band++) {
            bands[order.get(band)] = band;
        }
        return bands;
    }

    private double midpoint(TransportModeConfig config) {
        return (config.minKmh() + config.maxKmh()) / 2.0;
    }

    private double[][] forwardBackward(double[][] emissions, double[][] transitions) {
        int chunkCount = emissions.length;
        int modeCount = emissions[0].length;
        double[][] alpha = new double[chunkCount][modeCount];
        double[][] beta = new double[chunkCount][modeCount];

        for (int mode = 0; mode < modeCount; mode++) {
            alpha[0][mode] = Math.log(1.0 / modeCount) + emissions[0][mode];
        }
        normalizeRow(alpha[0]);
        for (int chunk = 1; chunk < chunkCount; chunk++) {
            for (int mode = 0; mode < modeCount; mode++) {
                double[] values = new double[modeCount];
                for (int previous = 0; previous < modeCount; previous++) {
                    values[previous] = alpha[chunk - 1][previous] + transitions[previous][mode];
                }
                alpha[chunk][mode] = logSumExp(values) + emissions[chunk][mode];
            }
            normalizeRow(alpha[chunk]);
        }

        for (int mode = 0; mode < modeCount; mode++) {
            beta[chunkCount - 1][mode] = 0.0;
        }
        for (int chunk = chunkCount - 2; chunk >= 0; chunk--) {
            for (int mode = 0; mode < modeCount; mode++) {
                double[] values = new double[modeCount];
                for (int next = 0; next < modeCount; next++) {
                    values[next] = transitions[mode][next] + emissions[chunk + 1][next] + beta[chunk + 1][next];
                }
                beta[chunk][mode] = logSumExp(values);
            }
            normalizeRow(beta[chunk]);
        }

        double[][] posteriors = new double[chunkCount][modeCount];
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            for (int mode = 0; mode < modeCount; mode++) {
                posteriors[chunk][mode] = alpha[chunk][mode] + beta[chunk][mode];
            }
            expRow(posteriors[chunk]);
        }
        return posteriors;
    }

    private void normalizeRow(double[] row) {
        double max = max(row);
        if (max == NEGATIVE_INFINITY) {
            return;
        }
        double sum = 0;
        for (int i = 0; i < row.length; i++) {
            row[i] = Math.exp(row[i] - max);
            sum += row[i];
        }
        for (int i = 0; i < row.length; i++) {
            row[i] = Math.log(row[i] / sum) + max;
        }
    }

    private void expRow(double[] row) {
        double max = max(row);
        double sum = 0;
        for (int i = 0; i < row.length; i++) {
            row[i] = Math.exp(row[i] - max);
            sum += row[i];
        }
        for (int i = 0; i < row.length; i++) {
            row[i] = row[i] / sum;
        }
    }

    private double logSumExp(double[] values) {
        double max = max(values);
        if (max == NEGATIVE_INFINITY) {
            return max;
        }
        double sum = 0;
        for (double value : values) {
            sum += Math.exp(value - max);
        }
        return max + Math.log(sum);
    }

    private double max(double[] values) {
        double max = NEGATIVE_INFINITY;
        for (double value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    public record DecodeResult(int[] states, List<TransportMode> modes, double[][] posteriors, double[] fit) {
    }
}
