package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
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
    private static final int PARTICLE_COUNT = 80;
    private static final float CONNECTION_DISTANCE = 150f;
    private static final float MOUSE_INFLUENCE_RADIUS = 200f;
    private static final float MOUSE_FORCE = 0.5f; // Positive = attract, negative = repel

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
                2f + random.nextFloat() * 4f,  // size: 2-6px
                0.3f + random.nextFloat() * 0.4f,  // alpha: 0.3-0.7
                (random.nextFloat() - 0.5f) * 0.5f,  // vx: -0.25 to 0.25
                (random.nextFloat() - 0.5f) * 0.5f,  // vy: -0.25 to 0.25
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
            // Wave motion along sine wave
            float waveOffset = (float) Math.sin(elapsedTime * p.waveSpeed + p.wavePhase) * 20f;

            // Base velocity
            float newX = p.x + p.vx * deltaTime * 60f;
            float newY = p.y + p.vy * deltaTime * 60f + waveOffset * deltaTime;

            // Mouse interaction
            float dx = p.x - mouseX;
            float dy = p.y - mouseY;
            float distToMouse = (float) Math.sqrt(dx * dx + dy * dy);

            if (distToMouse < MOUSE_INFLUENCE_RADIUS && distToMouse > 0.1f) {
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
        for (int i = 0; i < particles.size(); i++) {
            Particle p1 = particles.get(i);
            for (int j = i + 1; j < particles.size(); j++) {
                Particle p2 = particles.get(j);

                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance < CONNECTION_DISTANCE) {
                    // Line alpha based on distance (closer = more opaque)
                    float lineAlpha = (1f - distance / CONNECTION_DISTANCE) * 0.2f;
                    int color = ARGB.color((int) (lineAlpha * 255), 255, 255, 255);

                    // Draw line as thin rectangle
                    drawLine(gui, (int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y, color);
                }
            }
        }

        // Draw particles
        for (Particle p : particles) {
            int alpha = (int) (p.alpha * 255);
            int color = ARGB.color(alpha, 255, 255, 255);

            int radius = (int) p.size;

            // Draw as filled circle (approximated with filled rect)
            CustomRectRenderer.drawRect(gui,
                (int) (p.x - radius), (int) (p.y - radius),
                radius * 2, radius * 2,
                color);
        }
    }

    private void drawLine(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int color) {
        // Bresenham's line algorithm - draw line as series of 1px rectangles
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            CustomRectRenderer.drawRect(gui, x1, y1, 1, 1, color);

            if (x1 == x2 && y1 == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
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
