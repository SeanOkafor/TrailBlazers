import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * ScoreScreen - Draws the post-boss score overlay in the centre of the panel.
 * 
 * ==================== HOW THE SCORE SCREEN WORKS ====================
 * 
 * After the level boss is defeated, this overlay appears centred on the panel.
 * It shows each player's damage done, time taken, and total score.
 * 
 * REVEAL TIMING:
 * The three rows of stats appear one at a time, one second apart, for dramatic
 * effect. At 100 FPS:
 *   - Row 1 (Damage Done): appears after 1 second  (100 frames)
 *   - Row 2 (Time Taken):  appears after 2 seconds (200 frames)
 *   - Row 3 (Total Score): appears after 3 seconds (300 frames)
 * 
 * SCORE FORMULA:
 *   totalScore = (damageDealt * 10) - (timeInSeconds * 2000)
 *   Minimum score is 0.
 * 
 * Once the score screen is fully shown, the player presses SPACE to return
 * to the main menu.
 * 
 * TEXT POSITIONS (relative to the score screen image, ~470x339):
 *   P1 Damage:  centred in box (124,119)–(215,140)
 *   P1 Time:    centred in box (124,153)–(215,174)
 *   P1 Score:   centred in box (130,276)–(217,295)
 *   P2 Damage:  centred in box (366,119)–(457,140)
 *   P2 Time:    centred in box (366,153)–(457,174)
 *   P2 Score:   centred in box (371,276)–(458,295)
 * ===================================================================
 */
public class ScoreScreen {
	
	// The background image (ScoreScreen.png, ~470x339)
	private BufferedImage image;
	
	// Display dimensions (native image size)
	private int imgWidth;
	private int imgHeight;
	
	// Whether the score screen is currently active/visible
	private boolean active = false;
	
	// Frame counter since activation (for staggered reveal)
	private int revealTick = 0;
	
	// Frames per reveal step (~1 second at 100 FPS)
	private static final int REVEAL_INTERVAL = 100;
	
	// Stats to display
	private int p1Damage = 0;
	private int p2Damage = 0;
	private int timeSeconds = 0;
	private int p1Score = 0;
	private int p2Score = 0;
	private boolean multiplayerMode = false;
	
	// Panel dimensions (for centring)
	private static final int PANEL_WIDTH = 1000;
	private static final int PANEL_HEIGHT = 1000;
	
	public ScoreScreen() {
		loadImage();
	}
	
	private void loadImage() {
		try {
			image = ImageIO.read(new File("res/New Graphics/ScoreScreen.png"));
			if (image != null) {
				imgWidth = image.getWidth();
				imgHeight = image.getHeight();
			}
		} catch (IOException e) {
			System.err.println("Error loading ScoreScreen.png: " + e.getMessage());
			e.printStackTrace();
			// Fallback dimensions if image fails to load
			imgWidth = 470;
			imgHeight = 339;
		}
	}
	
	/**
	 * Activates the score screen with the given stats.
	 * Called when the level boss is defeated.
	 * 
	 * @param p1Damage     Player 1's total damage dealt
	 * @param p2Damage     Player 2's total damage dealt (0 if single player)
	 * @param elapsedSecs  Time spent in the level (seconds)
	 * @param multiplayer  Whether Player 2 stats should be shown
	 */
	public void activate(int p1Damage, int p2Damage, double elapsedSecs, boolean multiplayer) {
		this.p1Damage = p1Damage;
		this.p2Damage = p2Damage;
		this.timeSeconds = (int) elapsedSecs;
		this.multiplayerMode = multiplayer;
		
		// Calculate scores: (damage * 10) - (timeSeconds * 2000), minimum 0
		this.p1Score = Math.max(0, (p1Damage * 10) - (timeSeconds * 2000));
		this.p2Score = Math.max(0, (p2Damage * 10) - (timeSeconds * 2000));
		
		this.active = true;
		this.revealTick = 0;
	}
	
	/**
	 * Deactivates and resets the score screen.
	 * Called when returning to the main menu.
	 */
	public void deactivate() {
		active = false;
		revealTick = 0;
	}
	
	/**
	 * Called once per frame while active. Advances the reveal timer.
	 */
	public void update() {
		if (!active) return;
		revealTick++;
	}
	
	/**
	 * Draws the score screen overlay centred on the panel.
	 * Stats appear one row at a time, one second apart.
	 */
	public void draw(Graphics2D g2d) {
		if (!active) return;
		
		// Centre the image on the 1000x1000 panel
		int drawX = (PANEL_WIDTH - imgWidth) / 2;
		int drawY = (PANEL_HEIGHT - imgHeight) / 2;
		
		// Draw background image
		if (image != null) {
			g2d.drawImage(image, drawX, drawY, imgWidth, imgHeight, null);
		}
		
		// Set font for stats text
		g2d.setFont(new Font("Arial", Font.BOLD, 14));
		g2d.setColor(Color.WHITE);
		
		// --- Row 1: Damage Done (appears after 1 second) ---
		if (revealTick >= REVEAL_INTERVAL) {
			// P1 Damage — centred in box (124,119)–(215,140) relative to image
			drawCentredText(g2d, String.valueOf(p1Damage), drawX + 124, drawY + 119, 91, 21);
			
			// P2 Damage — centred in box (366,119)–(457,140) relative to image
			if (multiplayerMode) {
				drawCentredText(g2d, String.valueOf(p2Damage), drawX + 366, drawY + 119, 91, 21);
			} else {
				drawCentredText(g2d, "N/A", drawX + 366, drawY + 119, 91, 21);
			}
		}
		
		// --- Row 2: Time Taken (appears after 2 seconds) ---
		if (revealTick >= REVEAL_INTERVAL * 2) {
			String timeText = timeSeconds + "s";
			
			// P1 Time — centred in box (124,153)–(215,174) relative to image
			drawCentredText(g2d, timeText, drawX + 124, drawY + 153, 91, 21);
			
			// P2 Time — centred in box (366,153)–(457,174) relative to image
			drawCentredText(g2d, timeText, drawX + 366, drawY + 153, 91, 21);
		}
		
		// --- Row 3: Total Score (appears after 3 seconds) ---
		if (revealTick >= REVEAL_INTERVAL * 3) {
			// P1 Score — centred in box (130,276)–(217,295) relative to image
			drawCentredText(g2d, String.valueOf(p1Score), drawX + 130, drawY + 276, 87, 19);
			
			// P2 Score — centred in box (371,276)–(458,295) relative to image
			if (multiplayerMode) {
				drawCentredText(g2d, String.valueOf(p2Score), drawX + 371, drawY + 276, 87, 19);
			} else {
				drawCentredText(g2d, "N/A", drawX + 371, drawY + 276, 87, 19);
			}
		}
	}
	
	/**
	 * Draws text centred within a rectangular area.
	 */
	private void drawCentredText(Graphics2D g2d, String text, int boxX, int boxY, int boxW, int boxH) {
		int textWidth = g2d.getFontMetrics().stringWidth(text);
		int textHeight = g2d.getFontMetrics().getAscent();
		int tx = boxX + (boxW - textWidth) / 2;
		int ty = boxY + (boxH + textHeight) / 2 - 2;  // -2 for visual centering
		g2d.drawString(text, tx, ty);
	}
	
	// ========== GETTERS ==========
	
	public boolean isActive() { return active; }
	
	/** Returns true if all 3 rows have been revealed (ready for SPACE to dismiss). */
	public boolean isFullyRevealed() {
		return active && revealTick >= REVEAL_INTERVAL * 3;
	}
}
