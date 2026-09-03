package org.jeecg.modules.ai.domain;

/**
 * Normalized coordinates; each value in [0,1], x + width <= 1, y + height <= 1.
 */
public final class BoundingBox {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public BoundingBox(
            double x,
            double y,
            double width,
            double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}
