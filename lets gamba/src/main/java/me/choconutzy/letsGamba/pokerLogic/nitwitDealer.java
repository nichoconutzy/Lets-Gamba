package me.choconutzy.letsGamba.pokerLogic;

import me.choconutzy.letsGamba.LetsGambaPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class nitwitDealer {

    // Constants
    private static final int WOOL_REQUIRED = 6;
    private static final double FIND_RADIUS = 10.0;
    private static final double ACTIVATION_DISTANCE = 3.0;

    // State
    private UUID dealerId;
    private Location center;
    private final List<Block> tableBlocks = new ArrayList<>();
    private boolean isSetupInProgress = false;
    private boolean waitingForHost = false;
    private BukkitTask proximityTask;

    // Reference back to the parent PokerTable
    private final PokerTable pokerTable;

    public nitwitDealer(PokerTable pokerTable) {
        this.pokerTable = pokerTable;
    }

    // ---------- DEALER / NITWIT LOGIC ----------

    public boolean setupDealerAndTable(Player player) {
        isSetupInProgress = true;

        // 1. Find Green Wool nearby
        List<Block> woolBlocks = findNearbyGreenWool(player.getLocation());
        if (woolBlocks.size() < WOOL_REQUIRED) {
            player.sendMessage(ChatColor.RED + "Not enough Green Wool nearby!");
            player.sendMessage(ChatColor.GRAY + "You need a 3x2 Green Wool table.");
            isSetupInProgress = false;
            return false;
        }
        this.tableBlocks.clear();
        this.tableBlocks.addAll(woolBlocks);

        Location tableCenter = getCentroid(woolBlocks);

        // 2. Find Nitwit (Green Villager)
        Villager nitwit = findNearbyNitwit(player.getLocation());
        if (nitwit == null) {
            player.sendMessage(ChatColor.RED + "No Nitwit found nearby.");
            isSetupInProgress = false;
            return false;
        }

        // 3. Calculate Dealer Destination
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (Block b : woolBlocks) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }

        double midX = (minX + maxX) / 2.0 + 0.5;
        double midZ = (minZ + maxZ) / 2.0 + 0.5;

        // Determine table orientation and calculate potential dealer positions
        Location c1, c2;
        int xLen = maxX - minX;
        int zLen = maxZ - minZ;

        if (xLen > zLen) {
            // Table is oriented along X-axis, dealer on Z sides
            c1 = new Location(tableCenter.getWorld(), midX, tableCenter.getY(), minZ - 0.5);
            c2 = new Location(tableCenter.getWorld(), midX, tableCenter.getY(), maxZ + 1.5);
        } else {
            // Table is oriented along Z-axis, dealer on X sides
            c1 = new Location(tableCenter.getWorld(), minX - 0.5, tableCenter.getY(), midZ);
            c2 = new Location(tableCenter.getWorld(), maxX + 1.5, tableCenter.getY(), midZ);
        }

        // Find solid ground for both positions
        Location c1Ground = findSafeGroundPosition(c1);
        Location c2Ground = findSafeGroundPosition(c2);

        // Check if we found valid ground for both positions
        if (c1Ground == null && c2Ground == null) {
            player.sendMessage(ChatColor.RED + "Cannot set up dealer - no valid ground found near the table!");
            player.sendMessage(ChatColor.GRAY + "Make sure there's solid ground next to the table.");
            isSetupInProgress = false;
            return false;
        }

        // Check for stairs (using ground-adjusted positions)
        boolean c1HasStairs = c1Ground != null && hasNeighboringStairs(c1Ground, tableCenter);
        boolean c2HasStairs = c2Ground != null && hasNeighboringStairs(c2Ground, tableCenter);

        Location targetLoc = null;

        // Selection logic: prioritize no stairs, then valid ground, then closest
        if (c1Ground != null && !c1HasStairs && c2Ground != null && !c2HasStairs) {
            // Both valid and no stairs - choose closest to player
            targetLoc = (player.getLocation().distanceSquared(c1Ground) < player.getLocation().distanceSquared(c2Ground))
                    ? c1Ground : c2Ground;
        } else if (c1Ground != null && !c1HasStairs) {
            // Only c1 is valid and has no stairs
            targetLoc = c1Ground;
        } else if (c2Ground != null && !c2HasStairs) {
            // Only c2 is valid and has no stairs
            targetLoc = c2Ground;
        } else if (c1Ground != null && c2Ground != null) {
            // Both have stairs - FAIL
            player.sendMessage(ChatColor.RED + "Cannot set up dealer - both sides of the table have stairs!");
            player.sendMessage(ChatColor.GRAY + "Remove stairs from at least one side of the table.");
            isSetupInProgress = false;
            return false;
        } else {
            // Only one has valid ground (must have stairs, but it's our only option)
            targetLoc = (c1Ground != null) ? c1Ground : c2Ground;
            player.sendMessage(ChatColor.YELLOW + "Warning: Dealer position has stairs nearby (only valid position found).");
        }

        // Validate final position before proceeding
        if (!isPositionSafe(targetLoc)) {
            player.sendMessage(ChatColor.RED + "Cannot set up dealer - the position is not safe!");
            isSetupInProgress = false;
            return false;
        }

        // 4. Initialize Game Data
        this.dealerId = nitwit.getUniqueId();
        this.center = tableCenter;

        // 5. Check if Dealer is ALREADY there
        if (nitwit.getLocation().distance(targetLoc) < 1.5) {
            player.sendMessage(ChatColor.GREEN + "Dealer is already at the table.");

            targetLoc.setDirection(tableCenter.toVector().subtract(targetLoc.toVector()));
            targetLoc.setPitch(0);
            nitwit.teleport(targetLoc);
            nitwit.setAI(false);
            nitwit.setInvulnerable(true);
            nitwit.setGravity(true);

            isSetupInProgress = false;
            startProximityCheck(player);
            return true;
        }

        // 6. Start Dealer Movement Routine
        startDealerWalkingRoutine(player, nitwit, targetLoc, tableCenter);
        return true;
    }

    private void startDealerWalkingRoutine(Player player, Villager dealer, Location targetPos, Location lookAtTarget) {
        player.sendMessage(ChatColor.GREEN + "The Nitwit is walking to the dealer's seat...");
        dealer.setAI(true);
        dealer.setTarget(null);
        if (dealer instanceof Mob) ((Mob) dealer).getPathfinder().moveTo(targetPos);

        new BukkitRunnable() {
            int ticks = 0;
            final int MAX_WAIT_TICKS = 100;

            @Override
            public void run() {
                if (dealerId == null || !dealer.isValid()) {
                    isSetupInProgress = false;
                    this.cancel();
                    return;
                }

                double dist = dealer.getLocation().distance(targetPos);

                if (dist < 1.2 || ticks >= MAX_WAIT_TICKS) {
                    Location finalLoc = targetPos.clone();
                    Vector dir = lookAtTarget.clone().toVector().subtract(finalLoc.toVector());
                    finalLoc.setDirection(dir);
                    finalLoc.setPitch(0);

                    dealer.teleport(finalLoc);
                    dealer.setAI(false);
                    dealer.setInvulnerable(true);
                    dealer.setGravity(true);

                    isSetupInProgress = false;
                    startProximityCheck(player);
                    this.cancel();
                    return;
                }

                if (ticks % 20 == 0 && ticks < MAX_WAIT_TICKS) {
                    if (dealer instanceof Mob) ((Mob) dealer).getPathfinder().moveTo(targetPos);
                }
                ticks += 5;
            }
        }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 5L);
    }

    private boolean hasNeighboringStairs(Location dealerLoc, Location faceTarget) {
        Vector direction = faceTarget.toVector().subtract(dealerLoc.toVector()).setY(0).normalize();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector left = right.clone().multiply(-1);

        Block rightBlock = dealerLoc.clone().add(right).getBlock();
        Block leftBlock = dealerLoc.clone().add(left).getBlock();

        return isStair(rightBlock) || isStair(leftBlock);
    }

    private boolean isStair(Block b) {
        return b != null && b.getType().name().endsWith("_STAIRS");
    }

    private void startProximityCheck(Player initiator) {
        waitingForHost = true;
        initiator.sendMessage(ChatColor.GREEN + "The Dealer has arrived! Walk to the table to open it.");

        proximityTask = new BukkitRunnable() {
            int secondsWaited = 0;
            final int TIMEOUT = 45;

            @Override
            public void run() {
                if (dealerId == null || center == null) {
                    this.cancel();
                    waitingForHost = false;
                    return;
                }

                boolean playerClose = false;
                if (initiator.isOnline() && initiator.getWorld().equals(center.getWorld())) {
                    if (initiator.getLocation().distance(center) <= ACTIVATION_DISTANCE) {
                        playerClose = true;
                    }
                }

                if (playerClose) {
                    waitingForHost = false;

                    Bukkit.broadcastMessage(ChatColor.GREEN + "[Poker] A table is open at "
                            + ChatColor.YELLOW + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                            + ChatColor.GREEN + "! Use " + ChatColor.GOLD + "/poker join" + ChatColor.GREEN + " to join the table.");

                    // Call back to PokerTable to handle the join
                    pokerTable.join(initiator);

                    this.cancel();
                    return;
                }

                secondsWaited++;

                if (secondsWaited >= TIMEOUT) {
                    if (initiator.isOnline()) {
                        initiator.sendMessage(ChatColor.RED + "Poker request timed out. You didn't approach the table.");
                    }
                    releaseDealer();
                    center = null;
                    waitingForHost = false;
                    this.cancel();
                }
            }
        }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 20L);
    }

    public void releaseDealer() {
        if (proximityTask != null && !proximityTask.isCancelled()) {
            proximityTask.cancel();
        }
        waitingForHost = false;

        if (dealerId == null) return;

        Entity e = Bukkit.getEntity(dealerId);
        if (e instanceof Villager) {
            Villager v = (Villager) e;
            v.setAI(true);
            v.setInvulnerable(false);
        }
        dealerId = null;
    }

    private List<Block> findNearbyGreenWool(Location center) {
        List<Block> blocks = new ArrayList<>();
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.getBlock().getRelative(x, y, z);
                    if (b.getType() == Material.GREEN_WOOL) {
                        blocks.add(b);
                    }
                }
            }
        }
        return blocks;
    }

    private Villager findNearbyNitwit(Location loc) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, FIND_RADIUS, FIND_RADIUS, FIND_RADIUS)) {
            if (e instanceof Villager) {
                Villager v = (Villager) e;
                if (v.getProfession() == Profession.NITWIT && v.isValid()) {
                    return v;
                }
            }
        }
        return null;
    }

    private Location getCentroid(List<Block> blocks) {
        if (blocks.isEmpty()) return null;
        double totalX = 0, totalY = 0, totalZ = 0;
        for (Block b : blocks) {
            totalX += b.getX() + 0.5;
            totalY += b.getY() + 0.5;
            totalZ += b.getZ() + 0.5;
        }
        return new Location(blocks.get(0).getWorld(), totalX / blocks.size(), totalY / blocks.size(), totalZ / blocks.size());
    }

    // ---------- SAFETY AND GROUND DETECTION ----------

    /**
     * Finds a safe ground position for the dealer, preventing clipping.
     * Searches up to 5 blocks down and 3 blocks up from the initial position.
     *
     * @param startPos The starting position to search from
     * @return A safe ground location, or null if none found
     */
    private Location findSafeGroundPosition(Location startPos) {
        if (startPos == null || startPos.getWorld() == null) {
            return null;
        }

        World world = startPos.getWorld();
        int startY = startPos.getBlockY();

        // Search downward first (up to 5 blocks down)
        for (int y = startY; y >= startY - 5; y--) {
            Location checkLoc = new Location(world, startPos.getX(), y, startPos.getZ());
            if (isValidGroundPosition(checkLoc)) {
                // Position entity on TOP of the solid block
                return checkLoc.clone().add(0, 1, 0);
            }
        }

        // If downward search failed, try upward (up to 3 blocks up)
        for (int y = startY + 1; y <= startY + 3; y++) {
            Location checkLoc = new Location(world, startPos.getX(), y, startPos.getZ());
            if (isValidGroundPosition(checkLoc)) {
                return checkLoc.clone().add(0, 1, 0);
            }
        }

        return null; // No valid ground found
    }

    /**
     * Checks if a location is valid ground (solid block with air above).
     *
     * @param loc The location to check (should be the potential ground block)
     * @return true if this is valid ground for the dealer
     */
    private boolean isValidGroundPosition(Location loc) {
        Block groundBlock = loc.getBlock();
        Block aboveGround = loc.clone().add(0, 1, 0).getBlock();
        Block twoAboveGround = loc.clone().add(0, 2, 0).getBlock();

        // Ground must be solid
        if (!groundBlock.getType().isSolid()) {
            return false;
        }

        // Can't be a dangerous block
        if (isDangerousBlock(groundBlock)) {
            return false;
        }

        // Must have at least 2 blocks of air above for the villager
        if (!isPassable(aboveGround) || !isPassable(twoAboveGround)) {
            return false;
        }

        return true;
    }

    /**
     * Comprehensive safety check for the final dealer position.
     *
     * @param loc The location to validate
     * @return true if the position is safe for the dealer
     */
    private boolean isPositionSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block ground = loc.clone().add(0, -1, 0).getBlock();

        // Must have solid ground below
        if (!ground.getType().isSolid() || isDangerousBlock(ground)) {
            return false;
        }

        // Must have passable space for feet and head
        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }

        // Check for suffocation risks
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a block is passable (air, plants, etc.).
     *
     * @param block The block to check
     * @return true if an entity can pass through it
     */
    private boolean isPassable(Block block) {
        Material type = block.getType();

        // Air is always passable
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return true;
        }

        // Dangerous fluids are not passable
        if (type == Material.LAVA) {
            return false;
        }

        // Non-solid blocks are generally passable
        if (!type.isSolid()) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a block is dangerous for the dealer to stand on.
     *
     * @param block The block to check
     * @return true if the block is dangerous
     */
    private boolean isDangerousBlock(Block block) {
        Material type = block.getType();

        // Dangerous blocks that should never be used as ground
        return type == Material.LAVA ||
                type == Material.FIRE ||
                type == Material.SOUL_FIRE ||
                type == Material.MAGMA_BLOCK ||
                type == Material.CACTUS ||
                type == Material.SWEET_BERRY_BUSH ||
                type == Material.WITHER_ROSE ||
                type.name().contains("PRESSURE_PLATE") ||
                type.name().contains("TRIPWIRE");
    }

    // ---------- GETTERS ----------

    public Location getCenter() {
        return center;
    }

    public UUID getDealerId() {
        return dealerId;
    }

    public List<Block> getTableBlocks() {
        return tableBlocks;
    }

    public boolean isSetupInProgress() {
        return isSetupInProgress;
    }

    public boolean isWaitingForHost() {
        return waitingForHost;
    }

    public boolean isLocationOverlapping(Location otherCenter) {
        if (this.center == null || otherCenter == null) return false;
        if (!this.center.getWorld().equals(otherCenter.getWorld())) return false;
        return this.center.distance(otherCenter) < 6.0;
    }
}