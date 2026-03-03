import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// Floating health pickup that drifts leftward in a sine-wave pattern.
// Restores 1 HP on contact (up to max 5). Spawn rates differ between
// single and multiplayer modes (handled in the level panel, not here).
public class HealthPack {
	
	// Shared sprite — loaded once via loadSharedImage(), passed into constructor
	private BufferedImage image;
	
	// Roughly one-third of player dimensions (150x60)
	private static final int DISPLAY_WIDTH = 60;
	private static final int DISPLAY_HEIGHT = 50;
	
	private double x;
	private double baseY;  // centre of the sine wave oscillation
	private double y;      // actual drawn Y (baseY + wave offset)
	
	private static final double DRIFT_SPEED = 2.0;     // leftward drift: 2 px/frame
	private static final double WAVE_AMPLITUDE = 40.0;  // vertical bob amplitude in pixels
	private static final double WAVE_PERIOD = 300.0;    // frames per full cycle (~3s at 100 FPS)
	
	private int tick = 0;       // frame counter driving the sine wave
	private boolean consumed = false;
	
	private static final int PANEL_WIDTH = 1000;
	private static final int PANEL_HEIGHT = 1000;
	
	// Initialise just off the right edge; startY is the centre of the wave path
	public HealthPack(BufferedImage image, double startY) {
		this.image = image;
		this.x = PANEL_WIDTH + 10;  // slightly off-screen right
		this.baseY = startY;
		this.y = startY;
	}
	
	// Advance position each frame: drift left and apply sine-wave bobbing.
	// Vertical formula: y = baseY + 40 * sin(tick * 2pi / 300)
	// This produces a smooth ~3-second oscillation around baseY.
	public void update() {
		tick++;
		x -= DRIFT_SPEED;
		// Sine wave: amplitude 40px, period 300 frames (~3s)
		y = baseY + WAVE_AMPLITUDE * Math.sin(tick * 2 * Math.PI / WAVE_PERIOD);
	}
	
	// Render the sprite at the current position (skip if consumed)
	public void draw(Graphics2D g2d) {
		if (image != null && !consumed) {
			g2d.drawImage(image, (int) x, (int) y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
		}
	}
	
	// AABB overlap test — checks this pack's bounding box against a player's
	public boolean collidesWithPlayer(int px, int py, int pw, int ph) {
		int hx = (int) x;
		int hy = (int) y;
		// Standard axis-aligned bounding box intersection check
		return hx < px + pw && hx + DISPLAY_WIDTH > px
		    && hy < py + ph && hy + DISPLAY_HEIGHT > py;
	}
	
	// True once the pack has drifted fully off the left edge
	public boolean isOffScreen() {
		return x + DISPLAY_WIDTH < -10;
	}
	
	// Mark as collected (removed from active list on next sweep)
	public void consume() {
		consumed = true;
	}
	
	public boolean isConsumed() {
		return consumed;
	}
	
	// Shared image loader — call once and pass the result to each new HealthPack
	public static BufferedImage loadSharedImage() {
		try {
			return ImageIO.read(new File("res/New Graphics/Extra/Healthpack.png"));
		} catch (IOException e) {
			System.err.println("Error loading Healthpack.png: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
}
