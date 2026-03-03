import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// LoserScreen – "You Lost" overlay shown when all players die.
// Centred 612×408 image with dim background; 50-frame delay before SPACE is accepted.
public class LoserScreen {
	
	private BufferedImage image;       // loserscreen.png (612×408)
	private int imgWidth;
	private int imgHeight;
	private boolean active = false;    // whether the overlay is visible
	private int activeTick = 0;        // frames since activation
	private static final int ACCEPT_DELAY = 50;   // 50 frames (~0.5 s) before SPACE works
	private static final int PANEL_WIDTH = 1000;   // panel size for centring
	private static final int PANEL_HEIGHT = 1000;
	
	public LoserScreen() {
		loadImage();
	}
	
	private void loadImage() {
		try {
			image = ImageIO.read(new File("res/New Graphics/loserscreen.png"));
			if (image != null) {
				imgWidth = image.getWidth();
				imgHeight = image.getHeight();
			}
		} catch (IOException e) {
			System.err.println("Error loading loserscreen.png: " + e.getMessage());
			e.printStackTrace();
			// Fallback dimensions if image fails to load
			imgWidth = 612;
			imgHeight = 408;
		}
	}
	
	// Activate when all players have died and fallen off screen.
	public void activate() {
		this.active = true;
		this.activeTick = 0;
	}
	
	// Deactivate and reset (called when returning to menu).
	public void deactivate() {
		active = false;
		activeTick = 0;
	}
	
	// Advance the accept-delay timer each frame.
	public void update() {
		if (!active) return;
		activeTick++;
	}
	
	// Draw the loser overlay centred on the panel.
	public void draw(Graphics2D g2d) {
		if (!active) return;
		
		// Semi-transparent black overlay to dim the background
		g2d.setColor(new Color(0, 0, 0, 150));
		g2d.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
		
		// Centre the 612×408 image on the 1000×1000 panel
		int drawX = (PANEL_WIDTH - imgWidth) / 2;
		int drawY = (PANEL_HEIGHT - imgHeight) / 2;
		
		if (image != null) {
			g2d.drawImage(image, drawX, drawY, imgWidth, imgHeight, null);
		}
	}
	
	public boolean isActive() { return active; }
	
	// True once the 50-frame accept delay has passed and SPACE can dismiss.
	public boolean isReady() {
		return active && activeTick >= ACCEPT_DELAY;
	}
}
