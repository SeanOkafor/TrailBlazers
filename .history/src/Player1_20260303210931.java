import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// Player1 — animated flying sprite with 4-frame animation, combat, special charge, and i-frames.
// Rendered after parallax layers. Controlled via W/S keys from MainWindow's KeyListener.
public class Player1 {
	
	// 4-frame flight animation sprites
	private BufferedImage[] frames = new BufferedImage[4];
	private int currentFrame = 0;
	private int animationTick = 0;
	
	// Frame change every 8 ticks (at 100 FPS = ~12.5 animation FPS)
	private static final int ANIMATION_DELAY = 8;
	
	// Display dimensions (scaled to suit 1000x1000 panel)
	private static final int DISPLAY_WIDTH = 150;
	private static final int DISPLAY_HEIGHT = 60;
	
	// Screen position
	private int x;
	private int y;
	
	// Movement speed (px/frame)
	private static final int MOVE_SPEED = 10;
	
	// Movement state (set by keyboard input from MainWindow)
	private boolean movingUp = false;
	private boolean movingDown = false;
	
	// Panel dimensions (for boundary clamping)
	private int panelWidth = 1000;
	private int panelHeight = 1000;
	
	// HP (5 per level)
	private static final int MAX_HP = 5;
	private int hp = MAX_HP;
	
	// Total damage dealt this level (resets per level, used for scoring)
	private int damageDealt = 0;
	
	// Attack sprites (3 frames each for regular and special projectiles)
	private BufferedImage[] attackFrames = new BufferedImage[3];
	private BufferedImage[] specialFrames = new BufferedImage[3];
	
	private static final int REGULAR_DAMAGE = 100;
	private static final int SPECIAL_DAMAGE = 500;
	// Special charge: accumulates from regular-attack damage. At 1500, unlocks a
	// special shot (500 dmg vs 100 regular). Resets to 0 after firing.
	private static final int SPECIAL_CHARGE_THRESHOLD = 1500;
	private int specialCharge = 0;
	
	// I-frames: 100 frames (1s at 100 FPS). Sprite flickers between 30% alpha ghost
	// and full visibility, toggling every 5 frames via (timer / FLICKER_INTERVAL) % 2.
	private int iFrameTimer = 0;
	private static final int IFRAME_DURATION = 100;
	private static final int FLICKER_INTERVAL = 5;
	
	// Death fall: drops at 5 px/frame when HP reaches 0 until off-screen
	private boolean falling = false;
	private boolean fallenOffScreen = false;
	private static final int FALL_SPEED = 5;
	
	public Player1() {
		loadFrames();
		// Spawn in the left quadrant (x = 100), vertically centred
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2;
	}
	
	// Loads all sprite frames (flight, attack, special) from Player1Sprites folder
	private void loadFrames() {
		String basePath = "res/New Graphics/Player1Sprites/";
		try {
			// Flight animation frames
			frames[0] = ImageIO.read(new File(basePath + "sprite-1-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "sprite-1-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "sprite-1-3.png"));
			frames[3] = ImageIO.read(new File(basePath + "sprite-1-4.png"));
			
			// Regular attack frames
			attackFrames[0] = ImageIO.read(new File(basePath + "P1attack-1.png"));
			attackFrames[1] = ImageIO.read(new File(basePath + "P1attack-2.png"));
			attackFrames[2] = ImageIO.read(new File(basePath + "P1attack-3.png"));
			
			// Special attack frames (note: inconsistent capitalisation in filenames)
			specialFrames[0] = ImageIO.read(new File(basePath + "P1Special-1.png"));
			specialFrames[1] = ImageIO.read(new File(basePath + "P1special-2.png"));
			specialFrames[2] = ImageIO.read(new File(basePath + "P1special-3.png"));
		} catch (IOException e) {
			System.err.println("Error loading Player 1 sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Per-frame update: handles death fall, animation cycling, i-frames, and movement
	public void update() {
		// Death fall overrides normal behaviour
		if (falling) {
			y += FALL_SPEED;
			if (y > panelHeight + 50) {
				fallenOffScreen = true;
			}
			return;  // skip normal update
		}
		
		// Trigger death fall when HP reaches 0
		if (hp <= 0 && !falling) {
			falling = true;
			return;
		}
		
		// 4-frame flight animation: cycles every 8 ticks (~12.5 FPS at 100 FPS loop)
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % frames.length;
		}
		
		// I-frame countdown
		if (iFrameTimer > 0) {
			iFrameTimer--;
		}
		
		// Vertical movement (W/S keys)
		if (movingUp) {
			y -= MOVE_SPEED;
		}
		if (movingDown) {
			y += MOVE_SPEED;
		}
		
		// Clamp Y to keep player on screen
		if (y < 0) {
			y = 0;
		}
		if (y > panelHeight - DISPLAY_HEIGHT) {
			y = panelHeight - DISPLAY_HEIGHT;
		}
	}
	
	// Draws current frame; applies 30% alpha ghost flicker during i-frames
	public void draw(Graphics2D g2d) {
		BufferedImage currentImage = frames[currentFrame];
		if (currentImage != null) {
			// I-frame flicker: timer / 5 gives the interval index; even = ghost, odd = solid.
			// This creates a visible toggle every 5 frames for the 100-frame i-frame duration.
			if (iFrameTimer > 0 && (iFrameTimer / FLICKER_INTERVAL) % 2 == 0) {
				// Ghost frame — 30% alpha
				Composite original = g2d.getComposite();
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				g2d.setComposite(original);
			} else {
				g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
			}
		}
	}
	
	// Movement controls (set by KeyListener in MainWindow)
	
	public void setMovingUp(boolean moving) {
		this.movingUp = moving;
	}
	
	public void setMovingDown(boolean moving) {
		this.movingDown = moving;
	}
	
	// Resets all state to spawn defaults (called on level entry)
	public void resetPosition() {
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2;
		currentFrame = 0;
		animationTick = 0;
		hp = MAX_HP;
		damageDealt = 0;
		specialCharge = 0;
		iFrameTimer = 0;
		falling = false;
		fallenOffScreen = false;
	}
	
	// Combat stat accessors
	public int getHp() { return hp; }
	public int getMaxHp() { return MAX_HP; }
	public int getDamageDealt() { return damageDealt; }
	
	// Reduces HP by amount (floors at 0)
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
	
	// Applies 1 damage and starts i-frames; returns false if already invincible
	public boolean hit() {
		if (iFrameTimer > 0 || hp <= 0) return false;
		takeDamage(1);
		iFrameTimer = IFRAME_DURATION;
		return true;
	}
	
	// Applies custom damage and starts i-frames; returns false if already invincible
	public boolean hit(int amount) {
		if (iFrameTimer > 0 || hp <= 0) return false;
		takeDamage(amount);
		iFrameTimer = IFRAME_DURATION;
		return true;
	}
	
	// True if currently in i-frames
	public boolean isInvincible() {
		return iFrameTimer > 0;
	}
	
	// Accumulates total damage dealt (for scoring)
	public void addDamageDealt(int amount) {
		damageDealt += amount;
	}
	
	// True if HP > 0
	public boolean isAlive() {
		return hp > 0;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
	
	// True once death-fall animation finishes off-screen
	public boolean hasFallenOffScreen() { return fallenOffScreen; }
	
	// True if currently in death-fall animation
	public boolean isFalling() { return falling; }
	
	// Fires a projectile from the right edge of the sprite, vertically centred.
	// Special (1000 dmg) if charge >= 1500, otherwise regular (100 dmg).
	public Projectile shoot() {
		boolean fireSpecial = (specialCharge >= SPECIAL_CHARGE_THRESHOLD);
		
		BufferedImage[] projectileFrames;
		int damage;
		
		if (fireSpecial) {
			// Special: 1000 dmg, reset charge to 0
			projectileFrames = specialFrames;
			damage = SPECIAL_DAMAGE;
			specialCharge = 0;
		} else {
			// Regular: 100 dmg
			projectileFrames = attackFrames;
			damage = REGULAR_DAMAGE;
		}
		
		// Spawn at right edge of sprite, vertically centred (special projectile is taller)
		int spawnX = x + DISPLAY_WIDTH;
		int projectileHeight = fireSpecial ? 75 : 30;
		int spawnY = y + (DISPLAY_HEIGHT / 2) - (projectileHeight / 2);
		
		return new Projectile(projectileFrames, spawnX, spawnY, damage, fireSpecial, 1);
	}
	
	// Adds to special charge meter (called when a regular attack hits)
	public void addSpecialCharge(int amount) {
		specialCharge += amount;
	}
	
	// Current special charge (for HUD)
	public int getSpecialCharge() { return specialCharge; }
	
	// Threshold to unlock a special attack
	public static int getSpecialChargeThreshold() { return SPECIAL_CHARGE_THRESHOLD; }
}
