package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Particle animation system for custom background.
 * Creates floating particles with wave motion, mouse interaction, and connection lines.
 */
public class ParticleSystem {
    private static final int PARTICLE_COUNT = 60;
    private static final float CONNECTION_DISTANCE = 200f;
    private static final float MOUSE_INFLUENCE_RADIUS = 250f;
    private static final float MOUSE_FORCE = -0.8f; // Negative = repel (push away)

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private int screenWidth;
    private int screenHeight;
    private float mouseX;
    private float mouseY;
    private long startTime;

    public ParticleSystem(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.startTime = System.currentTimeMillis();

        // Initialize particles
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(
                random.nextFloat() * screenWidth,
                random.nextFloat() * screenHeight,
                3f,  // size: fixed 3px for consistency
                0.5f + random.nextFloat() * 0.2f,  // alpha: 0.5-0.7 (more visible)
                (random.nextFloat() - 0.5f) * 0.3f,  // vx: slower movement
                (random.nextFloat() - 0.5f) * 0.3f,  // vy: slower movement
                random.nextFloat() * (float) Math.PI * 2,  // wave phase
                0.5f + random.nextFloat() * 1f  // wave speed
            ));
        }
    }

    public void updateMousePosition(float mouseX, float mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public void resize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void update(float deltaTime) {
        float elapsedTime = (System.currentTimeMillis() - startTime) / 1000f;

        for (Particle p : particles) {
            // Wave motion along sine wave (reduced amplitude)
            float waveOffset = (float) Math.sin(elapsedTime * p.waveSpeed + p.wavePhase) * 10f;

            // Base velocity
            float newX = p.x + p.vx * deltaTime * 60f;
            float newY = p.y + p.vy * deltaTime * 60f + waveOffset * deltaTime * 0.3f; // Slower wave

            // Mouse interaction
            float dx = p.x - mouseX;
            float dy = p.y - mouseY;
            float distSq = dx * dx + dy * dy; // Use squared distance to avoid sqrt

            if (distSq < MOUSE_INFLUENCE_RADIUS * MOUSE_INFLUENCE_RADIUS && distSq > 0.1f) {
                float distToMouse = (float) Math.sqrt(distSq);
                float influence = 1f - (distToMouse / MOUSE_INFLUENCE_RADIUS);
                float force = MOUSE_FORCE * influence;

                // Normalize and apply force
                newX += (dx / distToMouse) * force * deltaTime * 60f;
                newY += (dy / distToMouse) * force * deltaTime * 60f;
            }

            // Wrap around screen edges
            if (newX < 0) newX += screenWidth;
            if (newX > screenWidth) newX -= screenWidth;
            if (newY < 0) newY += screenHeight;
            if (newY > screenHeight) newY -= screenHeight;

            p.x = newX;
            p.y = newY;
        }
    }

    public void render(GuiGraphicsExtractor gui) {
        // Draw connection lines first (so particles render on top)
        // Use squared distance for performance
        float connDistSq = CONNECTION_DISTANCE * CONNECTION_DISTANCE;

        for (int i = 0; i < particles.size(); i++) {
            Particle p1 = particles.get(i);
            for (int j = i + 1; j < particles.size(); j++) {
                Particle p2 = particles.get(j);

                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distanceSq = dx * dx + dy * dy;

                if (distanceSq < connDistSq) {
                    float distance = (float) Math.sqrt(distanceSq);
                    // Line alpha based on distance (closer = more opaque)
                    float lineAlpha = (1f - distance / CONNECTION_DISTANCE) * 0.25f; // Slightly more visible
                    int alpha = (int) (lineAlpha * 255);
                    int color = ARGB.color(alpha, 255, 255, 255);

                    // Draw line using simple rect between points
                    drawSimpleLine(gui, (int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y, color);
                }
            }
        }

        // Draw particles as circles
        for (Particle p : particles) {
            int alpha = (int) (p.alpha * 255);
            int color = ARGB.color(alpha, 255, 255, 255);

            int radius = (int) p.size;
            int diameter = radius * 2;

            // Draw as rounded rect with full rounding = circle
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                (int) (p.x - radius), (int) (p.y - radius),
                diameter, diameter,
                radius, // corner radius = radius for perfect circle
                color);
        }
    }

    // Draw proper line using Bresenham algorithm
    private void drawSimpleLine(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;

        while (true) {
            // Draw pixel
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, 1, 1, 0, color);

            if (x == x2 && y == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static class Particle {
        float x, y;           // Position
        float size;           // Radius
        float alpha;          // Transparency
        float vx, vy;         // Base velocity
        float wavePhase;      // Starting phase for wave motion
        float waveSpeed;      // Speed of wave oscillation

        Particle(float x, float y, float size, float alpha, float vx, float vy, float wavePhase, float waveSpeed) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.alpha = alpha;
            this.vx = vx;
            this.vy = vy;
            this.wavePhase = wavePhase;
            this.waveSpeed = waveSpeed;
        }
    }
}
