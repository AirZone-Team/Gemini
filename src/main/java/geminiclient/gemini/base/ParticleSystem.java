package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple and efficient particle network system.
 */
public class ParticleSystem {
    private static final int PARTICLE_COUNT = 30;
    private static final float CONNECTION_DISTANCE = 150f;
    private static final int MAX_CONNECTIONS = 2;

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private int screenWidth;
    private int screenHeight;

    public ParticleSystem(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        // Create particles with random positions
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(
                random.nextFloat() * screenWidth,
                random.nextFloat() * screenHeight,
                (random.nextFloat() - 0.5f) * 0.5f, // vx: slower
                (random.nextFloat() - 0.5f) * 0.5f  // vy: slower
            ));
        }
    }

    public void updateMousePosition(float mouseX, float mouseY) {
        // Mouse interaction: push particles away in opposite direction
        for (Particle p : particles) {
            float dx = p.x - mouseX;
            float dy = p.y - mouseY;
            float distSq = dx * dx + dy * dy;
            float influenceRadius = 200f;
            float minDist = influenceRadius * influenceRadius;

            if (distSq < minDist && distSq > 1f) {
                float dist = (float) Math.sqrt(distSq);
                // Stronger force when closer
                float force = (1f - dist / influenceRadius) * 3f;
                // Push away: add to position in direction away from mouse
                p.x += (dx / dist) * force;
                p.y += (dy / dist) * force;
            }
        }
    }

    public void resize(int width, int height) {
        float scaleX = width / (float) this.screenWidth;
        float scaleY = height / (float) this.screenHeight;
        this.screenWidth = width;
        this.screenHeight = height;

        for (Particle p : particles) {
            p.x *= scaleX;
            p.y *= scaleY;
        }
    }

    public void update(float deltaTime) {
        // Simple position update
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;

            // Wrap around edges
            if (p.x < 0) p.x += screenWidth;
            if (p.x > screenWidth) p.x -= screenWidth;
            if (p.y < 0) p.y += screenHeight;
            if (p.y > screenHeight) p.y -= screenHeight;
        }
    }

    public void render(GuiGraphicsExtractor gui, float partialTicks) {
        float connDistSq = CONNECTION_DISTANCE * CONNECTION_DISTANCE;

        // Draw connections (max 2 per particle)
        int[] counts = new int[particles.size()];
        for (int i = 0; i < particles.size(); i++) {
            if (counts[i] >= MAX_CONNECTIONS) continue;

            Particle p1 = particles.get(i);
            for (int j = i + 1; j < particles.size(); j++) {
                if (counts[i] >= MAX_CONNECTIONS || counts[j] >= MAX_CONNECTIONS) continue;

                Particle p2 = particles.get(j);
                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distSq = dx * dx + dy * dy;

                if (distSq < connDistSq) {
                    float dist = (float) Math.sqrt(distSq);
                    float alpha = (1f - dist / CONNECTION_DISTANCE) * 0.2f;
                    int color = ARGB.color((int)(alpha * 255), 255, 255, 255);

                    // Draw line (use rounded positions)
                    drawLine(gui, Math.round(p1.x), Math.round(p1.y),
                            Math.round(p2.x), Math.round(p2.y), color);

                    counts[i]++;
                    counts[j]++;
                }
            }
        }

        // Draw particles (use float positions for smooth rendering)
        for (Particle p : particles) {
            int color = ARGB.color(128, 255, 255, 255);
            // Round to nearest pixel instead of truncating
            int x = Math.round(p.x);
            int y = Math.round(p.y);
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                x - 2, y - 2, 4, 4, 2, color);
        }
    }

    private void drawLine(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 2) return;

        // Sample every 8 pixels
        int steps = Math.max(2, (int)(length / 8));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float)steps;
            int x = (int)(x1 + dx * t);
            int y = (int)(y1 + dy * t);
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, 2, 2, 0, color);
        }
    }

    private static class Particle {
        float x, y;
        float vx, vy;

        Particle(float x, float y, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }
}
