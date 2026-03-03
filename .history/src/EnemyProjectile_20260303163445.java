import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

// Represents a projectile fired by a boss with looping animation.
// Moves in a configured direction and damages players on contact.
public class EnemyProjectile {
	
	private BufferedImage[] frames;
	private int currentFrame = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 8;
	
	private double x, y;
	private double speedX;
	private double speedY;
	private int displayWidth;
	private int displayHeight;
	
	// Whether this projectile has been consumed (hit a player)
	private boolean consumed = false;
	
	// Initialise projectile with animation frames, position, speed, and display size.
	public EnemyProjectile(BufferedImage[] frames, double x, double y,
	                        double speedX, double speedY, int displayW, int displayH) {
		this.frames = frames;
		this.x = x;
		this.y = y;
		this.speedX = speedX;
		this.speedY = speedY;
		this.displayWidth = displayW;
		this.displayHeight = displayH;
	}
	
	// Update position and advance the animation frame.
	public void update() {
		x += speedX; // Apply horizontal movement
		y += speedY; // Apply vertical movement
		
		// Cycle through animation frames on a timed delay
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % frames.length;
		}
	}
	
	// Draw the current animation frame at the projectile's position.
	public void draw(Graphics2D g2d) {
		if (frames[currentFrame] != null) { // Guard against null frames
			g2d.drawImage(frames[currentFrame], (int) x, (int) y, displayWidth, displayHeight, null);
		}
	}
	
	// Check AABB collision between this projectile and a player's bounding box.
	public boolean collidesWithPlayer(int px, int py, int pw, int ph) {
		return x < px + pw &&
		       x + displayWidth > px &&
		       y < py + ph &&
		       y + displayHeight > py;
	}
	
	// Returns true if the projectile has moved off the visible 1000x1000 screen.
	public boolean isOffScreen() {
		return x + displayWidth < -50 || x > 1050 ||
		       y + displayHeight < -50 || y > 1050;
	}
	
	public void consume() { consumed = true; }
	public boolean isConsumed() { return consumed; }
	
	public void setSpeedX(double speedX) { this.speedX = speedX; }
	public void setSpeedY(double speedY) { this.speedY = speedY; }
	
	public double getX() { return x; }
	public double getY() { return y; }
	public int getDisplayWidth() { return displayWidth; }
	public int getDisplayHeight() { return displayHeight; }
}
