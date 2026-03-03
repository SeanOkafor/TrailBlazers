import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * Level2Panel - Renders a parallax scrolling industrial background for Level 2.
 * Uses 4 layers from the parallax-industrial-pack, each scrolling at different speeds.
 * 
 * ==================== HOW THE PARALLAX ANIMATION WORKS ====================
 * 
 * The parallax effect creates an illusion of depth by scrolling multiple background
 * layers at different speeds. Layers further "away" (like the sky) scroll slowly,
 * while layers closer to the "camera" (like foreground buildings) scroll faster.
 * 
 * SETUP:
 * 1. Each layer is a small PNG image (e.g. 272x160 pixels).
 * 2. Each layer has its own scroll offset (a double value tracking how far it has scrolled).
 * 3. Each layer has a fixed speed (pixels per frame) - back layers are slower, front layers faster.
 * 
 * PER-FRAME UPDATE (called from the main game loop in MainWindow.java):
 * 1. updateParallax() is called, which adds each layer's speed to its offset.
 *    e.g. bgOffset += 0.2, farBuildingsOffset += 0.5, etc.
 * 2. repaint() is called, which triggers paintComponent() to redraw the panel.
 * 
 * RENDERING (paintComponent → drawLayer for each layer, back to front):
 * 1. The layer image is scaled up to fill the panel height (1000px) while
 *    maintaining its aspect ratio. This gives us the "scaledWidth" for tiling.
 *    e.g. a 272x160 image scaled to 1000px tall → scaledWidth = 272 * (1000/160) = 1700px
 * 
 * 2. The offset is wrapped using modulo (%) so it doesn't grow forever:
 *    wrappedOffset = offset % scaledWidth
 *    This creates a seamless loop - when one tile scrolls fully off-screen,
 *    it wraps back around.
 * 
 * 3. Starting from x = -wrappedOffset, we tile the image across the panel width:
 *    for (x = startX; x < panelWidth; x += scaledWidth) { draw tile at x }
 *    This places copies of the image side by side, shifted left by the offset.
 * 
 * 4. If there's a gap on the left (startX > 0), we draw one extra tile before it.
 * 
 * The result: each layer scrolls continuously left at its own speed, creating
 * a smooth parallax depth effect. The game loop runs at ~100 FPS, so the 
 * offsets increment 100 times per second, producing fluid animation.
 * =========================================================================
 */
public class Level2Panel extends JPanel {
	
	// Player 1 sprite (always loaded, regardless of multiplayer mode)
	private Player1 player1;
	
	// Player 2 sprite (only loaded when multiplayer is enabled)
	private Player2 player2;
	
	// ========== ACTIVE PROJECTILES ==========
	// All projectiles currently flying across this level
	// Updated and drawn each frame; removed when off-screen or on enemy hit
	private List<Projectile> projectiles = new ArrayList<>();
	
	// ========== FINAL BOSS ==========
	private FinalBoss finalBoss;
	
	// ========== SCORE SCREEN ==========
	private ScoreScreen scoreScreen = new ScoreScreen();
	private boolean scoreScreenTriggered = false;
	
	// ========== LOSER SCREEN ==========
	private LoserScreen loserScreen = new LoserScreen();
	private boolean loserScreenTriggered = false;
	
	// ========== HEALTH PACKS ==========
	private java.awt.image.BufferedImage healthPackImage = HealthPack.loadSharedImage();
	private List<HealthPack> healthPacks = new ArrayList<>();
	private int healthPackSpawnTimer = 0;
	private static final int HP_SPAWN_INTERVAL_SP = 1500;  // 15 seconds at 100 FPS
	private static final int HP_SPAWN_INTERVAL_MP = 750;   // 7.5 seconds at 100 FPS
	private Random healthPackRng = new Random();
	
	// ========== LEVEL TIMER & SCORING ==========
	private long levelStartTime = 0;
	private double elapsedSeconds = 0;
	
	private static final double DAMAGE_MULTIPLIER = 100.0;
	private static final double TIME_PENALTY = 5.0;
	
	// Parallax layers (back to front) - 4 layers for industrial theme
	private BufferedImage bgLayer;             // sky/gradient background (slowest)
	private BufferedImage farBuildingsLayer;    // distant factory silhouettes
	private BufferedImage buildingsLayer;       // closer industrial buildings
	private BufferedImage foregroundLayer;      // foreground pipes/structures (fastest)
	
	// Scroll offsets - each layer tracks how many pixels it has scrolled
	// These are doubles for sub-pixel precision (allows fractional speeds like 0.2 px/frame)
	private double bgOffset = 0;
	private double farBuildingsOffset = 0;
	private double buildingsOffset = 0;
	private double foregroundOffset = 0;
	
	// Scroll speeds (pixels per frame) - creates the parallax depth illusion
	// Slower = appears further away, Faster = appears closer to the viewer
	private static final double BG_SPEED = 0.2;            // barely moves - distant sky
	private static final double FAR_BUILDINGS_SPEED = 0.6;  // slow - far background
	private static final double BUILDINGS_SPEED = 1.2;      // medium - mid-ground
	private static final double FOREGROUND_SPEED = 2.5;     // fast - closest to camera
	
	public Level2Panel() {
		setDoubleBuffered(true);  // prevents flickering during repaint
		loadLayers();
		player1 = new Player1();
	}
	
	/** Returns the Player1 instance so MainWindow can send movement commands. */
	public Player1 getPlayer1() {
		return player1;
	}
	
	/** Returns the Player2 instance (may be null if multiplayer is off). */
	public Player2 getPlayer2() {
		return player2;
	}
	
	/** Creates or removes Player 2 based on multiplayer toggle. */
	public void setMultiplayer(boolean enabled) {
		if (enabled) {
			if (player2 == null) player2 = new Player2();
		} else {
			player2 = null;
		}
	}
	
	public void resetTimer() {
		levelStartTime = System.currentTimeMillis();
		elapsedSeconds = 0;
		projectiles.clear();  // remove any leftover projectiles from previous play
		finalBoss = null;  // clear old boss so defeated state doesn't persist
		scoreScreenTriggered = false;
		scoreScreen.deactivate();
		loserScreenTriggered = false;
		loserScreen.deactivate();
		healthPacks.clear();
		healthPackSpawnTimer = 0;
	}
	
	public double getElapsedSeconds() {
		return elapsedSeconds;
	}
	
	public int calculateScore(int damageDealt) {
		double raw = (damageDealt * DAMAGE_MULTIPLIER) - (elapsedSeconds * TIME_PENALTY);
		return Math.max(0, (int) raw);
	}
	
	/** Adds a projectile to the active list (called when a player shoots). */
	public void addProjectile(Projectile p) {
		projectiles.add(p);
	}
	
	/** Returns the list of active projectiles (for collision detection). */
	public List<Projectile> getProjectiles() {
		return projectiles;
	}
	
	/** Spawns the Final Boss for this level. Called from MainWindow on level entry. */
	public void spawnFinalBoss(boolean multiplayer) {
		finalBoss = new FinalBoss(multiplayer, player1, player2);
	}
	
	/** Returns the Final Boss instance (may be null). */
	public FinalBoss getFinalBoss() {
		return finalBoss;
	}
	
	/** Returns the score screen (for MainWindow to check if active). */
	public ScoreScreen getScoreScreen() {
		return scoreScreen;
	}
	
	/** Returns the loser screen (for MainWindow to check if active). */
	public LoserScreen getLoserScreen() {
		return loserScreen;
	}
	
	private void loadLayers() {
		String basePath = "res/New Graphics/parallax-industrial-pack/parallax-industrial-pack/layers/";
		try {
			bgLayer = ImageIO.read(new File(basePath + "skill-desc_0003_bg.png"));
			farBuildingsLayer = ImageIO.read(new File(basePath + "skill-desc_0002_far-buildings.png"));
			buildingsLayer = ImageIO.read(new File(basePath + "skill-desc_0001_buildings.png"));
			foregroundLayer = ImageIO.read(new File(basePath + "skill-desc_0000_foreground.png"));
		} catch (IOException e) {
			System.err.println("Error loading parallax layers: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Called once per frame from the main game loop to advance all scroll offsets.
	 * Each layer's offset grows at its own speed, creating differential scrolling.
	 */
	public void updateParallax() {
		bgOffset += BG_SPEED;
		farBuildingsOffset += FAR_BUILDINGS_SPEED;
		buildingsOffset += BUILDINGS_SPEED;
		foregroundOffset += FOREGROUND_SPEED;
		
		// Update elapsed time
		if (levelStartTime > 0) {
			elapsedSeconds = (System.currentTimeMillis() - levelStartTime) / 1000.0;
		}
		
		// Update Player 1 animation and movement each frame
		if (player1 != null) {
			player1.update();
		}
		// Update Player 2 animation and movement (only exists in multiplayer)
		if (player2 != null) {
			player2.update();
		}
		
		// Update all active projectiles (move right + animate)
		// Check for collisions with the final boss, then remove off-screen ones
		Iterator<Projectile> it = projectiles.iterator();
		while (it.hasNext()) {
			Projectile p = it.next();
			p.update();
			
			// --- Collision with final boss ---
			if (finalBoss != null && finalBoss.isAlive() && finalBoss.collidesWith(p)) {
				finalBoss.takeDamage(p.getDamage());
				
				// Track damage on the player who fired this projectile
				if (p.getOwnerPlayer() == 1 && player1 != null) {
					player1.addDamageDealt(p.getDamage());
					if (!p.isSpecial()) {
						player1.addSpecialCharge(p.getDamage());
					}
				} else if (p.getOwnerPlayer() == 2 && player2 != null) {
					player2.addDamageDealt(p.getDamage());
					if (!p.isSpecial()) {
						player2.addSpecialCharge(p.getDamage());
					}
				}
				
				it.remove();
				continue;
			}
			
			if (p.isOffScreen()) {
				it.remove();
			}
		}
		
		// Update the final boss (entry animation, phase transitions, attacks, etc.)
		if (finalBoss != null && !finalBoss.isDefeated()) {
			finalBoss.update();
			
			// Check enemy projectiles → player collisions
			for (EnemyProjectile ep : finalBoss.getEnemyProjectiles()) {
				if (ep.isConsumed()) continue;
				
				// Check Player 1
				if (player1 != null && !player1.isInvincible()) {
					if (ep.collidesWithPlayer(player1.getX(), player1.getY(),
					    player1.getDisplayWidth(), player1.getDisplayHeight())) {
						player1.hit();
						ep.consume();
						continue;
					}
				}
				
				// Check Player 2
				if (player2 != null && !player2.isInvincible()) {
					if (ep.collidesWithPlayer(player2.getX(), player2.getY(),
					    player2.getDisplayWidth(), player2.getDisplayHeight())) {
						player2.hit();
						ep.consume();
					}
				}
			}
		}
		
		// Check if the boss was just defeated — trigger score screen
		if (finalBoss != null && finalBoss.isDefeated() && !scoreScreenTriggered) {
			scoreScreenTriggered = true;
			int p1Dmg = (player1 != null) ? player1.getDamageDealt() : 0;
			int p2Dmg = (player2 != null) ? player2.getDamageDealt() : 0;
			boolean mp = (player2 != null);
			scoreScreen.activate(p1Dmg, p2Dmg, elapsedSeconds, mp);
		}
		
		// ========== HEALTH PACK SPAWNING & COLLISION ==========
		healthPackSpawnTimer++;
		int spawnInterval = (player2 != null) ? HP_SPAWN_INTERVAL_MP : HP_SPAWN_INTERVAL_SP;
		if (healthPackSpawnTimer >= spawnInterval) {
			healthPackSpawnTimer = 0;
			double randomY = 50 + healthPackRng.nextInt(850);
			healthPacks.add(new HealthPack(healthPackImage, randomY));
		}
		
		// Update health packs and check collisions
		Iterator<HealthPack> hpIt = healthPacks.iterator();
		while (hpIt.hasNext()) {
			HealthPack hp = hpIt.next();
			hp.update();
			
			// Check Player 1 collision
			if (player1 != null && player1.isAlive() && !hp.isConsumed()) {
				if (hp.collidesWithPlayer(player1.getX(), player1.getY(),
				    player1.getDisplayWidth(), player1.getDisplayHeight())) {
					if (player1.heal()) {
						hp.consume();
					}
				}
			}
			
			// Check Player 2 collision
			if (player2 != null && player2.isAlive() && !hp.isConsumed()) {
				if (hp.collidesWithPlayer(player2.getX(), player2.getY(),
				    player2.getDisplayWidth(), player2.getDisplayHeight())) {
					if (player2.heal()) {
						hp.consume();
					}
				}
			}
			
			// Remove consumed or off-screen packs
			if (hp.isConsumed() || hp.isOffScreen()) {
				hpIt.remove();
			}
		}
		
		// Update score screen reveal timer
		if (scoreScreen.isActive()) {
			scoreScreen.update();
		}
		
		// Check if all players have died and fallen off screen — trigger loser screen
		if (!loserScreenTriggered && !scoreScreenTriggered) {
			boolean allDead = false;
			if (player2 != null) {
				allDead = player1.hasFallenOffScreen() && player2.hasFallenOffScreen();
			} else {
				allDead = player1.hasFallenOffScreen();
			}
			if (allDead) {
				loserScreenTriggered = true;
				loserScreen.activate();
			}
		}
		
		// Update loser screen timer
		if (loserScreen.isActive()) {
			loserScreen.update();
		}
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		
		int panelWidth = getWidth();
		int panelHeight = getHeight();
		
		// Draw layers back-to-front so closer layers paint over distant ones
		drawLayer(g2d, bgLayer, bgOffset, panelWidth, panelHeight);
		drawLayer(g2d, farBuildingsLayer, farBuildingsOffset, panelWidth, panelHeight);
		drawLayer(g2d, buildingsLayer, buildingsOffset, panelWidth, panelHeight);
		drawLayer(g2d, foregroundLayer, foregroundOffset, panelWidth, panelHeight);
		
		// Draw Player 1 on top of all parallax layers
		if (player1 != null) {
			player1.draw(g2d);
		}
		// Draw Player 2 (only in multiplayer mode)
		if (player2 != null) {
			player2.draw(g2d);
		}
		
		// Draw all active projectiles on top of players
		for (Projectile p : projectiles) {
			p.draw(g2d);
		}
		
		// Draw health packs
		for (HealthPack hp : healthPacks) {
			hp.draw(g2d);
		}
		
		// Draw the Final Boss on top of projectiles
		if (finalBoss != null && !finalBoss.isDefeated()) {
			finalBoss.draw(g2d);
		}
		
		// ========== DRAW HUD (timer + HP) ==========
		drawHUD(g2d);
		
		// ========== DRAW SCORE SCREEN (on top of everything) ==========
		if (scoreScreen.isActive()) {
			scoreScreen.draw(g2d);
		}
		
		// ========== DRAW LOSER SCREEN (on top of everything) ==========
		if (loserScreen.isActive()) {
			loserScreen.draw(g2d);
		}
	}
	
	private void drawHUD(Graphics2D g2d) {
		int minutes = (int) (elapsedSeconds / 60);
		int seconds = (int) (elapsedSeconds % 60);
		String timeText = String.format("Time: %02d:%02d", minutes, seconds);
		
		g2d.setFont(new Font("Arial", Font.BOLD, 35));
		g2d.setColor(Color.WHITE);
		int timeWidth = g2d.getFontMetrics().stringWidth(timeText);
		g2d.drawString(timeText, (getWidth() - timeWidth) / 2, 40);
		
		if (player1 != null) {
			String p1Text = "P1 HP: " + buildHPString(player1.getHp(), player1.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.RED);
			g2d.drawString(p1Text, 15, 80);
		}
		
		if (player2 != null) {
			String p2Text = "P2 HP: " + buildHPString(player2.getHp(), player2.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.BLUE);
			g2d.drawString(p2Text, 15, 115);
		}
	}
	
	private String buildHPString(int current, int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++) {
			sb.append(i < current ? "\u2665" : "\u2661");
		}
		return sb.toString();
	}
	
	/**
	 * Draws a single parallax layer, tiled horizontally and scrolling left.
	 * 
	 * How it works:
	 * - The small layer image (e.g. 272x160) is scaled to fill the full panel height (1000px)
	 * - The scaled width is calculated proportionally (maintains aspect ratio)
	 * - The image is tiled (repeated) horizontally across the panel
	 * - The offset shifts all tiles left, creating the scrolling effect
	 * - Modulo wrapping ensures seamless infinite looping
	 */
	private void drawLayer(Graphics2D g2d, BufferedImage layer, double offset, int panelWidth, int panelHeight) {
		if (layer == null) return;
		
		// Step 1: Scale the tiny layer image to fill the panel height
		// e.g. if layer is 272x160 and panel is 1000px tall:
		//   scale = 1000 / 160 = 6.25
		//   scaledWidth = 272 * 6.25 = 1700px
		double scale = (double) panelHeight / layer.getHeight();
		int scaledWidth = (int) (layer.getWidth() * scale);
		
		// Step 2: Wrap offset using modulo so it loops seamlessly
		// When offset reaches scaledWidth, it resets to 0
		double wrappedOffset = offset % scaledWidth;
		
		// Step 3: Tile the image from left to right, shifted by the offset
		// Starting at -wrappedOffset ensures smooth leftward scrolling
		int startX = (int) -wrappedOffset;
		for (int x = startX; x < panelWidth; x += scaledWidth) {
			g2d.drawImage(layer, x, 0, scaledWidth, panelHeight, null);
		}
		
		// Step 4: Fill any gap on the left edge with one extra tile
		if (startX > 0) {
			g2d.drawImage(layer, startX - scaledWidth, 0, scaledWidth, panelHeight, null);
		}
	}
}
