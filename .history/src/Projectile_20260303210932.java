import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

// Animated projectile that flies rightward at 8 px/frame.
// Regular attacks are 60x30 (100 dmg); special attacks are 150x75 (500 dmg).
// Removed when off-screen or on enemy collision.
public class Projectile {
	
	private BufferedImage[] frames;
	
	// Ping-pong sequence: cycles 0→1→2→1→0... giving a smooth back-and-forth animation
	private static final int[] ANIM_SEQUENCE = {0, 1, 2, 1};
	private int currentSequenceIndex = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 6; // ticks between frame changes (~16.7 anim FPS at 100 FPS)
	
	private int x;
	private int y;
	private static final int SPEED = 8; // pixels per frame
	
	// Special attacks use larger sprites (150x75) vs regular (60x30)
	private int displayWidth;
	private int displayHeight;
	private int damage;
	private boolean special;
	private int ownerPlayer; // tracks which player fired this for damage statistics
	
	// Initialise projectile at given position with damage, type, and owner
	public Projectile(BufferedImage[] frames, int x, int y, int damage, boolean special, int ownerPlayer) {
		this.frames = frames;
		this.x = x;
		this.y = y;
		this.damage = damage;
		this.special = special;
		this.ownerPlayer = ownerPlayer;
		
		// Special attacks render at 150x75; regular at 60x30
		if (special) {
			displayWidth = 150;
			displayHeight = 75;
		} else {
			displayWidth = 60;
			displayHeight = 30;
		}
	}
	
	// Move rightward and advance the ping-pong animation each frame
	public void update() {
		x += SPEED;
		
		// Advance animation; modulo wraps the index back through the ping-pong sequence
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentSequenceIndex = (currentSequenceIndex + 1) % ANIM_SEQUENCE.length;
		}
	}
	
	// Draw the current animation frame at this projectile's position
	public void draw(Graphics2D g2d) {
		int frameIndex = ANIM_SEQUENCE[currentSequenceIndex];
		if (frames != null && frames[frameIndex] != null) {
			g2d.drawImage(frames[frameIndex], x, y, displayWidth, displayHeight, null);
		}
	}
	
	// True if the projectile has passed the right edge of the screen
	public boolean isOffScreen() {
		return x > 1000;
	}
	
	// Getters for collision detection
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return displayWidth; }
	public int getDisplayHeight() { return displayHeight; }
	public int getDamage() { return damage; }
	public boolean isSpecial() { return special; }
	public int getOwnerPlayer() { return ownerPlayer; }
}
