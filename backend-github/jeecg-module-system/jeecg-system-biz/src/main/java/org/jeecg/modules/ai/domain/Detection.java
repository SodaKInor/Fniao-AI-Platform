package org.jeecg.modules.ai.domain;

/**
 * One normalized detection; no provider JSON or persistence fields.
 */
public final class Detection {
    private final String label;
    private final double score;
    private final BoundingBox box;

    public Detection(
            String label,
            double score,
            BoundingBox box) {
        this.label = label;
        this.score = score;
        this.box = box;
    }

    public String getLabel() {
        return label;
    }

    public double getScore() {
        return score;
    }

    public BoundingBox getBox() {
        return box;
    }
}
