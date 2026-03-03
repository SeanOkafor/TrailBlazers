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
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * Level1Panel - Renders a parallax scrolling mountain background for Level 1.
 * Uses 5 layers from the parallax_mountain_pack, each scrolling at different speeds.
 * 
 * ==================== HOW THE PARALLAX ANIMATION WORKS ====================
 * 
 * The parallax effect creates an illusion of depth by scrolling multiple background
 * layers at different speeds. Layers further "away" (like the sky) scroll slowly,
 * while layers closer to the "camera" (like foreground trees) scroll faster.
 * 
 * SETUP:
 * 1. Each layer is a small PNG image (e.g. 272x160 or 544x160 pixels).
 * 2. Each layer has its own scroll offset (a double value tracking how far it has scrolled).
 * 3. Each layer has a fixed speed (pixels per frame) - back layers are slower, front layers faster.
 * 
 * PER-FRAME UPDATE (called from the main game loop in MainWindow.java):
 * 1. updateParallax() is called, which adds each layer's speed to its offset.
 *    e.g. bgOffset += 0.2, mountainFarOffset += 0.5, etc.
 * 2. repaint() is called, which triggers paintComponent() to redraw the panel.
 * 
 * RENDERING (paintComponent -> drawLayer for each layer, back to front):
 * 1. The layer image is scaled up to fill the panel height (1000px) while
 *    maintaining its aspect ratio. This gives us the "scaledWidth" for tiling.
 *    e.g. a 272x160 image scaled to 1000px tall -> scaledWidth = 272 * (1000/160) = 1700px
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
public class Level1Panel extends JPanel {
	
	// Player 1 sprite (always loaded, regardless of multiplayer mode)
	private Player1 player1;
	
	// Player 2 sprite (only loaded when multiplayer is enabled)
	private Player2 player2;
	
	// ========== ACTIVE PROJECTILES ==========
	// All projectiles currently flying across this level
	// Updated and drawn each frame; removed when off-screen or on enemy hit
	private List<Projectile> projectiles = new ArrayList<>();
	
	// ========== TUTORIAL ENEMY (Level 1 only) ==========
	// The first encounter — a block that bobs up and down on the right side.
	// Created when the level starts; null until then.
	private TutorialEnemy tutorialEnemy;
	
	// ========== FIRST BOSS (Level 1, after tutorial enemy) ==========
	// 2-phase boss that spawns after the tutorial block is defeated.
	private FirstBoss firstBoss;
	private boolean firstBossSpawned = false;
	
	// ========== SCORE SCREEN ==========
	private ScoreScreen scoreScreen = new ScoreScreen();
	private boolean scoreScreenTriggered = false;
	
	// ========== LEVEL TIMER & SCORING ==========
	// Tracks when the level started (System.currentTimeMillis at level entry)
	private long levelStartTime = 0;
	// Elapsed seconds since level started (updated each frame)
	private double elapsedSeconds = 0;
	
	// Score formula constants: score = (damageDealt * DAMAGE_MULTIPLIER) - (seconds * TIME_PENALTY)
	private static final double DAMAGE_MULTIPLIER = 100.0;  // points per unit of damage dealt
	private static final double TIME_PENALTY = 5.0;          // points lost per second
	
	// Parallax layers (back to front)
	private BufferedImage bgLayer;           // sky background (slowest)
	private BufferedImage mountainFarLayer;   // distant mountains
	private BufferedImage mountainsLayer;     // closer mountains
	private BufferedImage treesLayer;         // trees
	private BufferedImage foregroundLayer;    // foreground trees (fastest)
	
	// Scroll offsets - each layer tracks how many pixels it has scrolled
	// These are doubles for sub-pixel precision (allows fractional speeds like 0.2 px/frame)
	private double bgOffset = 0;
	private double mountainFarOffset = 0;
	private double mountainsOffset = 0;
	private double treesOffset = 0;
	private double foregroundOffset = 0;
	
	// Scroll speeds (pixels per frame) - creates the parallax depth illusion
	// Slower = appears further away, Faster = appears closer to the viewer
	private static final double BG_SPEED = 0.2;            // barely moves - distant sky
	private static final double MOUNTAIN_FAR_SPEED = 0.5;  // slow - far mountains
	private static final double MOUNTAINS_SPEED = 1.0;     // medium - closer mountains
	private static final double TREES_SPEED = 1.5;         // faster - mid-ground trees
	private static final double FOREGROUND_SPEED = 2.5;    // fastest - closest to camera
	
	public Level1Panel() {
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
	
	/** Resets the level timer. Called when entering/replaying the level. */
	public void resetTimer() {
		levelStartTime = System.currentTimeMillis();
		elapsedSeconds = 0;
		projectiles.clear();  // remove any leftover projectiles from previous play
		tutorialEnemy = null;
		firstBoss = null;
		firstBossSpawned = false;
		scoreScreenTriggered = false;
		scoreScreen.deactivate();
	}
	
	/**
	 * Spawns the tutorial enemy for this level.
	 * Called from MainWindow when entering Level 1, after multiplayer mode is set.
	 * @param multiplayer true if 2-player mode (enemy gets double HP)
	 */
	public void spawnTutorialEnemy(boolean multiplayer) {
		tutorialEnemy = new TutorialEnemy(multiplayer);
	}
	
	/** Returns the tutorial enemy instance (may be null). */
	public TutorialEnemy getTutorialEnemy() {
		return tutorialEnemy;
	}
	
	/** Returns the first boss instance (may be null). */
	public FirstBoss getFirstBoss() {
		return firstBoss;
	}
	
	/** Returns the score screen (for MainWindow to check if active). */
	public ScoreScreen getScoreScreen() {
		return scoreScreen;
	}
	
	/** Returns elapsed seconds since level started. */
	public double getElapsedSeconds() {
		return elapsedSeconds;
	}
	
	/**
	 * Calculates score for a player: (damageDealt * DAMAGE_MULTIPLIER) - (elapsedSeconds * TIME_PENALTY).
	 * Minimum score is 0.
	 */
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
	
	/**
	 * Tracks a projectile hit: adds damage dealt and special charge to the
	 * player who fired the projectile.
	 */
	private void trackProjectileHit(Projectile p) {
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
	}
	
	private void loadLayers() {
		String basePath = "res/New Graphics/parallax_mountain_pack/parallax_mountain_pack/layers/";
		try {
			bgLayer = ImageIO.read(new File(basePath + "parallax-mountain-bg.png"));
			mountainFarLayer = ImageIO.read(new File(basePath + "parallax-mountain-montain-far.png"));
			mountainsLayer = ImageIO.read(new File(basePath + "parallax-mountain-mountains.png"));
			treesLayer = ImageIO.read(new File(basePath + "parallax-mountain-trees.png"));
			foregroundLayer = ImageIO.read(new File(basePath + "parallax-mountain-foreground-trees.png"));
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
		mountainFarOffset += MOUNTAIN_FAR_SPEED;
		mountainsOffset += MOUNTAINS_SPEED;
		treesOffset += TREES_SPEED;
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
		
		// Update the tutorial enemy (vertical oscillation or death slide)
		if (tutorialEnemy != null && !tutorialEnemy.isDefeated()) {
			tutorialEnemy.update();
		}
		
		// Update the first boss (phase transitions, animation, etc.)
		if (firstBoss != null && !firstBoss.isDefeated()) {
			firstBoss.update();
		}
		
		// Update all active projectiles (move right + animate)
		// Check for collisions with the tutorial enemy, then remove off-screen ones
		Iterator<Projectile> it = projectiles.iterator();
		while (it.hasNext()) {
			Projectile p = it.next();
			p.update();
			
			// --- Collision with tutorial enemy ---
			if (tutorialEnemy != null && tutorialEnemy.isAlive() && tutorialEnemy.collidesWith(p)) {
				tutorialEnemy.takeDamage(p.getDamage());
				trackProjectileHit(p);
				it.remove();
				continue;
			}
			
			// --- Collision with first boss ---
			if (firstBoss != null && firstBoss.isAlive() && firstBoss.collidesWith(p)) {
				firstBoss.takeDamage(p.getDamage());
				trackProjectileHit(p);
				it.remove();
				continue;
			}
			
			if (p.isOffScreen()) {
				it.remove();
			}
		}
		
		// When tutorial enemy is defeated, spawn the first boss
		if (tutorialEnemy != null && tutorialEnemy.isDefeated() && !firstBossSpawned) {
			firstBossSpawned = true;
			firstBoss = new FirstBoss(player2 != null);
		}
		
		// Check if the first boss was defeated — trigger score screen
		if (firstBoss != null && firstBoss.isDefeated() && !scoreScreenTriggered) {
			scoreScreenTriggered = true;
			int p1Dmg = (player1 != null) ? player1.getDamageDealt() : 0;
			int p2Dmg = (player2 != null) ? player2.getDamageDealt() : 0;
			boolean mp = (player2 != null);
			scoreScreen.activate(p1Dmg, p2Dmg, elapsedSeconds, mp);
		}
		
		// Update score screen reveal timer
		if (scoreScreen.isActive()) {
			scoreScreen.update();
		}
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		
		int panelWidth = getWidth();
		int panelHeight = getHeight();
		
		// Draw each layer, tiled horizontally and scaled to fill the panel height
		drawLayer(g2d, bgLayer, bgOffset, panelWidth, panelHeight);
		drawLayer(g2d, mountainFarLayer, mountainFarOffset, panelWidth, panelHeight);
		drawLayer(g2d, mountainsLayer, mountainsOffset, panelWidth, panelHeight);
		drawLayer(g2d, treesLayer, treesOffset, panelWidth, panelHeight);
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
		
		// Draw the tutorial enemy (on top of parallax, alongside players)
		if (tutorialEnemy != null && !tutorialEnemy.isDefeated()) {
			tutorialEnemy.draw(g2d);
		}
		
		// Draw the first boss (after tutorial enemy is defeated)
		if (firstBoss != null && !firstBoss.isDefeated()) {
			firstBoss.draw(g2d);
		}
		
		// ========== DRAW HUD (timer + HP) ==========
		drawHUD(g2d);
		
		// ========== DRAW SCORE SCREEN (on top of everything) ==========
		if (scoreScreen.isActive()) {
			scoreScreen.draw(g2d);
		}
	}
	
	/**
	 * Draws the heads-up display: level timer (top-centre) and player HP bars.
	 * The timer is always visible so players can track their time.
	 * HP is shown as "P1 HP: ♥♥♥♥♥" near each player's area.
	 */
	private void drawHUD(Graphics2D g2d) {
		// --- Timer (top-centre) ---
		int minutes = (int) (elapsedSeconds / 60);
		int seconds = (int) (elapsedSeconds % 60);
		String timeText = String.format("Time: %02d:%02d", minutes, seconds);
		
		g2d.setFont(new Font("Arial", Font.BOLD, 35));
		g2d.setColor(Color.WHITE);
		int timeWidth = g2d.getFontMetrics().stringWidth(timeText);
		g2d.drawString(timeText, (getWidth() - timeWidth) / 2, 40);
		
		// --- Player 1 HP (top-left) ---
		if (player1 != null) {
			String p1Text = "P1 HP: " + buildHPString(player1.getHp(), player1.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.RED);
			g2d.drawString(p1Text, 15, 80);
		}
		
		// --- Player 2 HP (below P1 HP, only in multiplayer) ---
		if (player2 != null) {
			String p2Text = "P2 HP: " + buildHPString(player2.getHp(), player2.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.BLUE);
			g2d.drawString(p2Text, 15, 115);
		}
	}
	
	/**
	 * Builds a heart-based HP string: filled hearts for remaining HP, empty for lost.
	 * e.g. 3/5 HP → "♥♥♥♡♡"
	 */
	private String buildHPString(int current, int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++) {
			sb.append(i < current ? "\u2665" : "\u2661");  // ♥ or ♡
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
