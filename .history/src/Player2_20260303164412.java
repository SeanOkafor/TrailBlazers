import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// Player 2 — same animation/combat system as Player1 but uses Player2 sprites
// and arrow keys. Spawns 100px below Player 1's centre in multiplayer mode.
public class Player2 {
	
	private BufferedImage[] frames = new BufferedImage[4]; // 4-frame flight animation
	private int currentFrame = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 8; // ticks between frame changes (~12.5 FPS)

	private static final int DISPLAY_WIDTH = 150;
	private static final int DISPLAY_HEIGHT = 60;

	private int x;
	private int y;
	private static final int MOVE_SPEED = 10;

	// Movement state — set by arrow key input from MainWindow
	private boolean movingUp = false;
	private boolean movingDown = false;

	private int panelWidth = 1000;
	private int panelHeight = 1000;
	
	// Combat stats
	private static final int MAX_HP = 5;
	private int hp = MAX_HP;
	private int damageDealt = 0; // cumulative damage dealt this level, used for scoring
	
	// Attack system — charge builds from regular hits; special fires at 1500 threshold
	private BufferedImage[] attackFrames = new BufferedImage[3];  // regular projectile frames
	private BufferedImage[] specialFrames = new BufferedImage[3]; // special projectile frames
	private static final int REGULAR_DAMAGE = 100;
	private static final int SPECIAL_DAMAGE = 1000;
	private static final int SPECIAL_CHARGE_THRESHOLD = 1500;
	private int specialCharge = 0; // resets to 0 after firing a special
	
	// I-frames — 30% alpha ghost every 5 frames during invincibility
	private int iFrameTimer = 0;
	private static final int IFRAME_DURATION = 100;  // 1 second at 100 FPS
	private static final int FLICKER_INTERVAL = 5;
	
	// Death fall — sprite drops off screen when HP reaches 0
	private boolean falling = false;
	private boolean fallenOffScreen = false;
	private static final int FALL_SPEED = 5;
	
	public Player2() {
		loadFrames();
		// Spawn left quadrant, 100px below Player 1's centre
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2 + 100;
	}
	
	// Loads all sprite frames (flight, attack, special) from Player2Sprites folder
	private void loadFrames() {
		String basePath = "res/New Graphics/Player2Sprites/";
		try {
			frames[0] = ImageIO.read(new File(basePath + "sprite-2-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "sprite-2-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "sprite-2-3.png"));
			frames[3] = ImageIO.read(new File(basePath + "sprite-2-4.png"));
			attackFrames[0] = ImageIO.read(new File(basePath + "P2attack-1.png"));
			attackFrames[1] = ImageIO.read(new File(basePath + "P2attack-2.png"));
			attackFrames[2] = ImageIO.read(new File(basePath + "P2attack-3.png"));
			specialFrames[0] = ImageIO.read(new File(basePath + "P2special-1.png"));
			specialFrames[1] = ImageIO.read(new File(basePath + "P2special-2.png"));
			specialFrames[2] = ImageIO.read(new File(basePath + "P2special-3.png"));
		} catch (IOException e) {
			System.err.println("Error loading Player 2 sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Per-frame update: handles death fall, animation cycling, i-frame countdown, and movement
	public void update() {
		// Death fall overrides all normal behaviour
		if (falling) {
			y += FALL_SPEED;
			if (y > panelHeight + 50) fallenOffScreen = true;
			return;
		}
		
		// Trigger fall when HP depleted
		if (hp <= 0 && !falling) {
			falling = true;
			return;
		}
		
		// Cycle through 4 animation frames
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % frames.length;
		}
		
		if (iFrameTimer > 0) iFrameTimer--;
		
		// Vertical movement via arrow keys
		if (movingUp) y -= MOVE_SPEED;
		if (movingDown) y += MOVE_SPEED;
		
		// Clamp to screen bounds
		if (y < 0) y = 0;
		if (y > panelHeight - DISPLAY_HEIGHT) y = panelHeight - DISPLAY_HEIGHT;
	}
	
	// Renders current frame; flickers at 30% alpha every 5 frames during i-frames
	public void draw(Graphics2D g2d) {
		BufferedImage currentImage = frames[currentFrame];
		if (currentImage != null) {
			// Alternating ghost effect: integer-divide timer by flicker interval, even = translucent
			if (iFrameTimer > 0 && (iFrameTimer / FLICKER_INTERVAL) % 2 == 0) {
				Composite original = g2d.getComposite();
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				g2d.setComposite(original);
			} else {
				g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
			}
		}
	}
	
	// Movement controls — called by KeyListener in MainWindow
	public void setMovingUp(boolean moving) {
		this.movingUp = moving;
	}
	
	public void setMovingDown(boolean moving) {
		this.movingDown = moving;
	}
	
	// Resets all state to spawn defaults (left quadrant, 100px below P1 centre)
	public void resetPosition() {
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2 + 100;
		currentFrame = 0;
		animationTick = 0;
		hp = MAX_HP;
		damageDealt = 0;
		specialCharge = 0;
		iFrameTimer = 0;
		falling = false;
		fallenOffScreen = false;
	}
	
	public int getHp() { return hp; }
	public int getMaxHp() { return MAX_HP; }
	public int getDamageDealt() { return damageDealt; }
	
	// Reduces HP (floored at 0)
	public void takeDamage(int amount) {
		hp -= amount;
		if (hp < 0) hp = 0;
	}
	
	// Heals 1 HP if below max; returns true if healed
	public boolean heal() {
		if (hp >= MAX_HP) return false;
		hp++;
		return true;
	}
	
	// Applies 1 heart of damage and activates i-frames; ignored if already invincible
	public boolean hit() {
		if (iFrameTimer > 0 || hp <= 0) return false;
		takeDamage(1);
		iFrameTimer = IFRAME_DURATION;
		return true;
	}
	
	// Overload: applies custom damage amount and activates i-frames
	public boolean hit(int amount) {
		if (iFrameTimer > 0 || hp <= 0) return false;
		takeDamage(amount);
		iFrameTimer = IFRAME_DURATION;
		return true;
	}
	
	public boolean isInvincible() {
		return iFrameTimer > 0;
	}
	
	// Accumulates damage dealt (for score calculation)
	public void addDamageDealt(int amount) {
		damageDealt += amount;
	}
	
	public boolean isAlive() {
		return hp > 0;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
	
	public boolean hasFallenOffScreen() { return fallenOffScreen; }
	public boolean isFalling() { return falling; }
	
	// Fires a projectile from the right edge of the sprite, vertically centred.
	// If charge >= 1500, fires special (1000 dmg) and resets; otherwise regular (100 dmg).
	public Projectile shoot() {
		boolean fireSpecial = (specialCharge >= SPECIAL_CHARGE_THRESHOLD);
		
		BufferedImage[] projectileFrames;
		int damage;
		
		if (fireSpecial) {
			projectileFrames = specialFrames;
			damage = SPECIAL_DAMAGE;
			specialCharge = 0; // reset charge after special
		} else {
			projectileFrames = attackFrames;
			damage = REGULAR_DAMAGE;
		}
		
		// Spawn at right edge, vertically centred on sprite
		int spawnX = x + DISPLAY_WIDTH;
		int projectileHeight = fireSpecial ? 75 : 30;
		int spawnY = y + (DISPLAY_HEIGHT / 2) - (projectileHeight / 2);
		
		return new Projectile(projectileFrames, spawnX, spawnY, damage, fireSpecial, 2);
	}
	
	// Adds to special charge meter when a regular hit lands
	public void addSpecialCharge(int amount) {
		specialCharge += amount;
	}
	
	public int getSpecialCharge() { return specialCharge; }
	public static int getSpecialChargeThreshold() { return SPECIAL_CHARGE_THRESHOLD; }
}
