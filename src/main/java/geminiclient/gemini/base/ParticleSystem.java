package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sparse particle network animation for custom background.
 * Low density, random distribution, subtle movement, minimal connections.
 */
public class ParticleSystem {
    private static final int PARTICLE_COUNT = 35; // Low density
    private static final float CONNECTION_DISTANCE = 120f; // Short distance only
    private static final int MAX_CONNECTIONS_PER_PARTICLE = 2; // Maximum 2 connections
    private static final float MOUSE_INFLUENCE_RADIUS = 180f;
    private static final float MOUSE_FORCE = -0.15f; // Very subtle effect

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

        // Initialize particles with random distribution (not uniform grid)
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(
                random.nextFloat() * screenWidth,
                random.nextFloat() * screenHeight,
                2f,  // Small size: 2px
                0.3f + random.nextFloat() * 0.2f,  // Low alpha: 0.3-0.5 (subtle)
                (random.nextFloat() - 0.5f) * 0.15f,  // Very slow: ±0.075
                (random.nextFloat() - 0.5f) * 0.15f,  // Very slow drift
                random.nextFloat() * (float) Math.PI * 2,  // wave phase
                0.3f + random.nextFloat() * 0.4f  // Slow wave: 0.3-0.7
            ));
        }
    }

    public void updateMousePosition(float mouseX, float mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public void resize(int width, int height) {
        float scaleX = width / (float) this.screenWidth;
        float scaleY = height / (float) this.screenHeight;

        this.screenWidth = width;
        this.screenHeight = height;

        // Redistribute particles proportionally to new screen size
        for (Particle p : particles) {
            p.x *= scaleX;
            p.y *= scaleY;
        }
    }

    public void update(float deltaTime) {
        float elapsedTime = (System.currentTimeMillis() - startTime) / 1000f;

        for (Particle p : particles) {
            // Subtle wave motion
            float waveOffset = (float) Math.sin(elapsedTime * p.waveSpeed + p.wavePhase) * 5f;

            // Very slow base velocity
            float newX = p.x + p.vx * deltaTime * 60f;
            float newY = p.y + p.vy * deltaTime * 60f + waveOffset * deltaTime * 0.2f;

            // Very subtle mouse interaction
            float dx = p.x - mouseX;
            float dy = p.y - mouseY;
            float distSq = dx * dx + dy * dy;

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

        // Track connections per particle to limit them
        int[] connectionCounts = new int[particles.size()];

        for (int i = 0; i < particles.size(); i++) {
            // Skip if this particle already has max connections
            if (connectionCounts[i] >= MAX_CONNECTIONS_PER_PARTICLE) continue;

            Particle p1 = particles.get(i);
            for (int j = i + 1; j < particles.size(); j++) {
                // Skip if either particle has max connections
                if (connectionCounts[i] >= MAX_CONNECTIONS_PER_PARTICLE ||
                    connectionCounts[j] >= MAX_CONNECTIONS_PER_PARTICLE) continue;

                Particle p2 = particles.get(j);

                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distanceSq = dx * dx + dy * dy;

                if (distanceSq < connDistSq) {
                    float distance = (float) Math.sqrt(distanceSq);
                    // Very low alpha for subtle effect
                    float lineAlpha = (1f - distance / CONNECTION_DISTANCE) * 0.15f;
                    int alpha = (int) (lineAlpha * 255);
                    int color = ARGB.color(alpha, 255, 255, 255);

                    // Draw line
                    drawSimpleLine(gui, (int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y, color);

                    // Increment connection counts
                    connectionCounts[i]++;
                    connectionCounts[j]++;
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

    // Draw true diagonal line using optimized sampling
    private void drawSimpleLine(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 2) return;

        // Sample every 3-4 pixels for balance between quality and performance
        int samples = Math.max(2, (int) (length / 3.5f));

        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            int x = (int) (x1 + dx * t);
            int y = (int) (y1 + dy * t);

            // Draw 1px point
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, 1, 1, 0, color);
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
