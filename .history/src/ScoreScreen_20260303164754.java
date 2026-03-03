import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// ScoreScreen – post-boss overlay showing damage, time and score for each player.
// Stats reveal in 3 staggered rows (1 s apart). Score = (damage*10)-(seconds*2000), min 0.
// Text is centred with a drop-shadow inside fixed boxes relative to a ~470×339 image.
public class ScoreScreen {
	
	private BufferedImage image;       // ScoreScreen.png (~470×339)
	private int imgWidth;
	private int imgHeight;
	private boolean active = false;    // whether the overlay is visible
	private int revealTick = 0;        // frame counter for staggered reveal
	private static final int REVEAL_INTERVAL = 100; // ~1 s at 100 FPS per row
	
	private int p1Damage = 0;
	private int p2Damage = 0;
	private int timeSeconds = 0;
	private int p1Score = 0;
	private int p2Score = 0;
	private boolean multiplayerMode = false;
	private static final int PANEL_WIDTH = 1000;  // panel size for centring
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
	
	// Initialise and display the score screen with the given stats.
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
	
	// Deactivate and reset (called when returning to menu).
	public void deactivate() {
		active = false;
		revealTick = 0;
	}
	
	// Advance the reveal timer each frame.
	public void update() {
		if (!active) return;
		revealTick++;
	}
	
	// Draw the overlay centred on the panel; rows appear one at a time.
	public void draw(Graphics2D g2d) {
		if (!active) return;
		
		// Semi-transparent black overlay to dim the background
		g2d.setColor(new Color(0, 0, 0, 150));
		g2d.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
		
		// Centre the score image on the panel
		int drawX = (PANEL_WIDTH - imgWidth) / 2;
		int drawY = (PANEL_HEIGHT - imgHeight) / 2;
		
		if (image != null) {
			g2d.drawImage(image, drawX, drawY, imgWidth, imgHeight, null);
		}
		
		g2d.setFont(new Font("Arial", Font.BOLD, 14));
		g2d.setColor(Color.WHITE);
		
		// Row 1 – Damage Done (visible after 100 frames / 1 s)
		if (revealTick >= REVEAL_INTERVAL) {
			// P1 damage centred in box (124,119)–(215,140)
			drawCentredText(g2d, String.valueOf(p1Damage), drawX + 124, drawY + 119, 91, 21);
			// P2 damage centred in box (366,119)–(457,140)
			if (multiplayerMode) {
				drawCentredText(g2d, String.valueOf(p2Damage), drawX + 366, drawY + 119, 91, 21);
			} else {
				drawCentredText(g2d, "N/A", drawX + 366, drawY + 119, 91, 21);
			}
		}
		
		// Row 2 – Time Taken (visible after 200 frames / 2 s)
		if (revealTick >= REVEAL_INTERVAL * 2) {
			String timeText = timeSeconds + "s";
			drawCentredText(g2d, timeText, drawX + 124, drawY + 153, 91, 21);  // P1 time box (124,153)–(215,174)
			drawCentredText(g2d, timeText, drawX + 366, drawY + 153, 91, 21);  // P2 time box (366,153)–(457,174)
		}
		
		// Row 3 – Total Score (visible after 300 frames / 3 s)
		if (revealTick >= REVEAL_INTERVAL * 3) {
			// P1 score centred in box (130,276)–(217,295)
			drawCentredText(g2d, String.valueOf(p1Score), drawX + 130, drawY + 276, 87, 19);
			// P2 score centred in box (371,276)–(458,295)
			if (multiplayerMode) {
				drawCentredText(g2d, String.valueOf(p2Score), drawX + 371, drawY + 276, 87, 19);
			} else {
				drawCentredText(g2d, "N/A", drawX + 371, drawY + 276, 87, 19);
			}
		}
	}
	
	// Draw text centred within a box, with a 1 px drop shadow for readability.
	private void drawCentredText(Graphics2D g2d, String text, int boxX, int boxY, int boxW, int boxH) {
		int textWidth = g2d.getFontMetrics().stringWidth(text);
		int textHeight = g2d.getFontMetrics().getAscent();
		int tx = boxX + (boxW - textWidth) / 2;          // horizontal centre
		int ty = boxY + (boxH + textHeight) / 2 - 2;      // vertical centre (-2 nudge)
		
		Color original = g2d.getColor();
		g2d.setColor(Color.BLACK);                         // shadow offset +1,+1
		g2d.drawString(text, tx + 1, ty + 1);
		g2d.setColor(original);                            // foreground colour
		g2d.drawString(text, tx, ty);
	}
	
	public boolean isActive() { return active; }
	
	// True once all 3 rows are revealed and the player can press SPACE.
	public boolean isFullyRevealed() {
		return active && revealTick >= REVEAL_INTERVAL * 3;
	}
}
