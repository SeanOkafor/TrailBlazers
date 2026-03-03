import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// Tutorial enemy for Level 1. Oscillates vertically on the right side of the screen.
// Uses 3 damage-state sprites (NOT animation): >60% HP = frame 0, 20-60% = frame 1, <20% = frame 2.
// HP: 5000 (SP) / 10000 (MP). Slides off-screen on death.
public class TutorialEnemy {
	
	// 3 damage-state sprite frames (not animation — chosen by HP percentage)
	private BufferedImage[] frames = new BufferedImage[3];
	
	private int x;
	private int y;
	
	// Display size: 50% larger than 263x263 source
	private static final int DISPLAY_WIDTH = 395;
	private static final int DISPLAY_HEIGHT = 395;
	
	// Collision insets: 100px each side shrinks hitbox to ~195x195, matching visible sprite
	private static final int COLLISION_INSET_X = 100;
	private static final int COLLISION_INSET_Y = 100;
	
	// Vertical oscillation: bobs between MIN_Y and MAX_Y at 2 px/frame
	private static final int MOVE_SPEED = 2;
	private boolean movingDown = true;
	private static final int MIN_Y = 50;
	private static final int MAX_Y = 555;
	
	// Health: SP=5000, MP=10000 (doubled for all bosses)
	private static final int SINGLE_PLAYER_HP = 5000;
	private static final int MULTIPLAYER_HP = 10000;
	private int maxHp;
	private int hp;
	
	// State
	private boolean alive = true;
	private boolean defeated = false;
	private static final int DEATH_SLIDE_SPEED = 5; // px/frame off right edge on death
	
	// Damage flash: red tint overlay for a few frames on hit
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;
	private static final float DAMAGE_FLASH_ALPHA = 0.45f;
	
	// Health bar
	private static final int HEALTH_BAR_WIDTH = 300;
	private static final int HEALTH_BAR_HEIGHT = 22;
	private static final int HEALTH_BAR_Y_OFFSET = -35;
	
	private int panelWidth = 1000;
	private int panelHeight = 1000;
	
	// Initialises the tutorial enemy; multiplayer doubles HP
	public TutorialEnemy(boolean multiplayer) {
		loadFrames();
		
		if (multiplayer) {
			maxHp = MULTIPLAYER_HP;
		} else {
			maxHp = SINGLE_PLAYER_HP;
		}
		hp = maxHp;
		
		// Spawn on right side, vertically centred
		x = panelWidth - DISPLAY_WIDTH - 50;
		y = (panelHeight - DISPLAY_HEIGHT) / 2;
	}
	
	// Loads the 3 damage-state sprite frames
	private void loadFrames() {
		String basePath = "res/New Graphics/Tutorial Enemy/";
		try {
			frames[0] = ImageIO.read(new File(basePath + "Tutorialblock-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "Tutorialblock-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "Tutorialblock-3.png"));
		} catch (IOException e) {
			System.err.println("Error loading Tutorial Enemy sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Per-frame update: oscillate vertically while alive, slide off-screen on death
	public void update() {
		if (defeated) return;
		
		if (damageFlashTimer > 0) {
			damageFlashTimer--;
		}
		
		if (alive) {
			// Vertical oscillation: reverse direction at bounds
			if (movingDown) {
				y += MOVE_SPEED;
				if (y >= MAX_Y) {
					y = MAX_Y;
					movingDown = false;
				}
			} else {
				y -= MOVE_SPEED;
				if (y <= MIN_Y) {
					y = MIN_Y;
					movingDown = true;
				}
			}
		} else {
			// Death slide: move off right edge at 5 px/frame
			x += DEATH_SLIDE_SPEED;
			if (x > panelWidth) {
				defeated = true;
			}
		}
	}
	
	// Draws the current damage-state sprite, flash overlay, and health bar
	public void draw(Graphics2D g2d) {
		if (defeated) return;
		
		// Select damage-state frame: >60% = 0, 20-60% = 1, <20% = 2
		int frameIndex;
		double hpPercent = (double) hp / maxHp;
		
		if (hpPercent > 0.6) {
			frameIndex = 0;
		} else if (hpPercent > 0.2) {
			frameIndex = 1;
		} else {
			frameIndex = 2;
		}
		
		if (frames[frameIndex] != null) {
			g2d.drawImage(frames[frameIndex], x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
			
			// Damage flash: draw sprite to temp buffer, then SRC_IN composite
			// red over it — only opaque pixels get tinted, preserving transparency
			if (damageFlashTimer > 0) {
				BufferedImage flashImage = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
				Graphics2D flashG = flashImage.createGraphics();
				flashG.drawImage(frames[frameIndex], 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, null); // alpha mask
				flashG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f)); // red only on opaque pixels
				flashG.setColor(Color.RED);
				flashG.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);
				flashG.dispose();
				
				// Overlay the red-tinted sprite at partial opacity
				Composite originalComposite = g2d.getComposite();
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DAMAGE_FLASH_ALPHA));
				g2d.drawImage(flashImage, x, y, null);
				g2d.setComposite(originalComposite);
			}
		}
		
		// Health bar above the sprite
		if (alive) {
			int barX = x + (DISPLAY_WIDTH - HEALTH_BAR_WIDTH) / 2; // centred
			int barY = y + HEALTH_BAR_Y_OFFSET;
			
			g2d.setColor(new Color(50, 50, 50));
			g2d.fillRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
			
			// Colour: green (>50%) -> yellow (20-50%) -> red (<20%)
			double hpRatio = (double) hp / maxHp;
			int fillWidth = (int) (HEALTH_BAR_WIDTH * hpRatio);
			if (hpRatio > 0.5) {
				g2d.setColor(new Color(50, 205, 50));
			} else if (hpRatio > 0.2) {
				g2d.setColor(new Color(255, 200, 0));
			} else {
				g2d.setColor(new Color(220, 30, 30));
			}
			g2d.fillRect(barX, barY, fillWidth, HEALTH_BAR_HEIGHT);
			
			g2d.setColor(Color.WHITE);
			g2d.drawRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
			
			// HP text centred in bar
			g2d.setFont(new Font("Arial", Font.BOLD, 14));
			String hpText = hp + " / " + maxHp;
			int textWidth = g2d.getFontMetrics().stringWidth(hpText);
			g2d.setColor(Color.WHITE);
			g2d.drawString(hpText, barX + (HEALTH_BAR_WIDTH - textWidth) / 2, barY + 16);
		}
	}
	
	// Applies damage; triggers flash and death behaviour at 0 HP
	public void takeDamage(int amount) {
		if (!alive) return;
		
		hp -= amount;
		damageFlashTimer = DAMAGE_FLASH_DURATION;
		if (hp <= 0) {
			hp = 0;
			alive = false;
		}
	}
	
	// AABB collision using inset hitbox (100px padding each side of 395x395 display)
	public boolean collidesWith(Projectile p) {
		if (!alive) return false;
		
		int hitX = x + COLLISION_INSET_X;
		int hitY = y + COLLISION_INSET_Y;
		int hitW = DISPLAY_WIDTH  - 2 * COLLISION_INSET_X;
		int hitH = DISPLAY_HEIGHT - 2 * COLLISION_INSET_Y;
		
		return p.getX() < hitX + hitW &&
		       p.getX() + p.getDisplayWidth() > hitX &&
		       p.getY() < hitY + hitH &&
		       p.getY() + p.getDisplayHeight() > hitY;
	}
	
	// Getters
	public boolean isAlive() { return alive; }
	public boolean isDefeated() { return defeated; }
	public int getHp() { return hp; }
	public int getMaxHp() { return maxHp; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
}
